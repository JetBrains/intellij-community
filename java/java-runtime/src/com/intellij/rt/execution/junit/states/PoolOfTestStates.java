package com.intellij.rt.execution.junit.states;

public interface PoolOfTestStates {
  int SKIPPED_INDEX = 0;
  int COMPLETE_INDEX = 1;
  int NOT_RUN_INDEX = 2;
  int RUNNING_INDEX = 3;
  int TERMINATED_INDEX = 4;
  int IGNORED_INDEX = 5;
  int FAILED_INDEX = 6;
  int COMPARISON_FAILURE = 7;
  int ERROR_INDEX = 8;
  int PASSED_INDEX = COMPLETE_INDEX;
}
