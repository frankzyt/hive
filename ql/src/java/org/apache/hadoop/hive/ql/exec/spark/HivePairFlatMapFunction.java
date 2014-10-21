package org.apache.hadoop.hive.ql.exec.spark;

import java.text.NumberFormat;

import org.apache.hadoop.mapred.JobConf;
import org.apache.spark.TaskContext;
import org.apache.spark.api.java.function.PairFlatMapFunction;


public abstract class HivePairFlatMapFunction<T, K, V> implements PairFlatMapFunction<T, K, V> {
  private static final NumberFormat taskIdFormat = NumberFormat.getInstance();
  private static final NumberFormat stageIdFormat = NumberFormat.getInstance();
  static {
    taskIdFormat.setGroupingUsed(false);
    taskIdFormat.setMinimumIntegerDigits(6);
    stageIdFormat.setGroupingUsed(false);
    stageIdFormat.setMinimumIntegerDigits(4);
  }

  protected transient JobConf jobConf;
  protected SparkReporter sparkReporter;

  private byte[] buffer;

  public HivePairFlatMapFunction(byte[] buffer, SparkReporter sparkReporter) {
    this.buffer = buffer;
    this.sparkReporter = sparkReporter;
  }

  protected void initJobConf() {
    if (jobConf == null) {
      jobConf = KryoSerializer.deserializeJobConf(this.buffer);
      setupMRLegacyConfigs();
    }
  }

  protected abstract boolean isMap();

  // Some Hive features depends on several MR configuration legacy, build and add
  // these configuration to JobConf here.
  private void setupMRLegacyConfigs() {
    StringBuilder taskAttemptIdBuilder = new StringBuilder("attempt_");
    taskAttemptIdBuilder.append(System.currentTimeMillis())
      .append("_")
      .append(stageIdFormat.format(TaskContext.get().stageId()))
      .append("_");

    if (isMap()) {
      taskAttemptIdBuilder.append("m_");
    } else {
      taskAttemptIdBuilder.append("r_");
    }

    // Spark task attempt id is increased by Spark context instead of task, which may introduce
    // unstable qtest output, since non Hive features depends on this, we always set it to 0 here.
    taskAttemptIdBuilder.append(taskIdFormat.format(TaskContext.get().partitionId()))
      .append("_0");

    String taskAttemptIdStr = taskAttemptIdBuilder.toString();
    jobConf.set("mapred.task.id", taskAttemptIdStr);
    jobConf.set("mapreduce.task.attempt.id", taskAttemptIdStr);
    jobConf.setInt("mapred.task.partition", TaskContext.get().partitionId());
  }
}