package org.jetbrains.jps.runConf.testng;

public interface TestListener {
  void suiteStarted(String suiteName);

  void suiteFinished(String suiteName);

  void testStarted(String testName, String flowId);

  void testFinishedSuccess(String testName, String flowId, int duration);

  void testFinishedFailure(String testName, String flowId, Throwable failure, int duration);

  void testFinishedIgnored(String testName, String flowId);

  void error(String message);
}
