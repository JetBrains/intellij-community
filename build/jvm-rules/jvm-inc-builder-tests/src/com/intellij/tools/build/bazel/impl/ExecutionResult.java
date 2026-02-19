package com.intellij.tools.build.bazel.impl;

import org.junit.Assert;

public interface ExecutionResult {

  int getExitCode();

  String getOutput();

  default boolean isSuccessful() {
    return getExitCode() == 0;
  }
  
  default boolean isFailure() {
    return getExitCode() != 0;
  }

  default void assertSuccessful() {
    if (!isSuccessful()) {
      Assert.fail("Build failed.\n" + getOutput());
    }
  }

  default void assertFailure() {
    if (isSuccessful()) {
      Assert.fail("Build successful, but is expected to fail.\n" + getOutput());
    }
  }

  static ExecutionResult create(int exitCode, String output) {
    return new ExecutionResult() {
      @Override
      public int getExitCode() {
        return exitCode;
      }

      @Override
      public String getOutput() {
        return output;
      }
    };
  }

}
