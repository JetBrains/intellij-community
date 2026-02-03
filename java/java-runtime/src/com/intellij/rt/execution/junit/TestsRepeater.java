// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.execution.junit;

public final class TestsRepeater {
  public interface TestRun {
    int execute(boolean sendTree) throws Exception;
  }

  public static int repeat(int count, boolean sendTree, TestRun testRun) throws Exception {
    if (count == 1) {
      return testRun.execute(sendTree);
    }
    else {
      boolean success = true;
      if (count > 0) {
        int i = 0;
        while (i++ < count) {
          final int result = testRun.execute(sendTree);
          if (result == -2) {
            return result;
          }
          success &= result == 0;
          sendTree = false;
        }

        return success ? 0 : -1;
      }
      else {
        while (true) {
          int result = testRun.execute(sendTree);
          if (result == -2) {
            return -1;
          }
          success &= result == 0;
          if (count == -2 && !success) {
            return -1;
          }
          if (count == -3 && result == 0) {
            return 0;
          }
        }
      }
    }
  }
}
