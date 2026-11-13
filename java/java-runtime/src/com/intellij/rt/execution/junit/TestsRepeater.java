// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.execution.junit;

import com.intellij.rt.execution.TestListenerProtocol;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TestsRepeater {
  public interface TestRun {
    /**
     * @param parentId the id of the tree node that gets the nodes of this run. It is null when the runner reports no node ids.
     */
    int execute(String parentId, boolean sendTree) throws Exception;
  }

  /**
   * @param parentId the id of the tree node that gets the nodes of the launch. Each repetition gets its own suite under this node.
   *                 It is null when the runner reports no node ids. Then the repetitions get no suite.
   */
  public static int repeat(int count, boolean sendTree, String parentId, TestRun testRun) throws Exception {
    if (count == 1) {
      return testRun.execute(parentId, sendTree);
    }
    else {
      Repetitions repetitions = new Repetitions(parentId, sendTree, testRun);
      boolean success = true;
      if (count > 0) {
        int i = 0;
        while (i++ < count) {
          final int result = repetitions.execute(i, sendTree);
          if (result == -2) {
            return result;
          }
          success &= result == 0;
          sendTree = false;
        }

        return success ? 0 : -1;
      }
      else {
        int repetition = 0;
        while (true) {
          int result = repetitions.execute(++repetition, sendTree);
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

  private static final class Repetitions {
    private final PrintStream myOut = System.out;
    private final String myParentId;
    private final boolean mySendTree;
    private final TestRun myTestRun;
    private final String myProcessId;

    private Repetitions(String parentId, boolean sendTree, TestRun testRun) {
      myParentId = parentId;
      mySendTree = sendTree;
      myTestRun = testRun;
      // A forked process sends no tree. Its sibling processes report the same repetitions, so the id of a repetition needs the process.
      myProcessId = sendTree ? "" : String.valueOf(System.currentTimeMillis());
    }

    private int execute(int repetition, boolean sendTree) throws Exception {
      if (myParentId == null) {
        return myTestRun.execute(null, sendTree);
      }
      String suiteName = "[" + repetition + "]";
      String scope = TestListenerProtocol.ROOT_NODE_ID.equals(myParentId) ? "" : myParentId + "/";
      String suiteId = scope + suiteName + myProcessId;
      Map<String, String> suite = new LinkedHashMap<>();
      suite.put("id", suiteId);
      suite.put("name", suiteName);
      suite.put("nodeId", suiteId);
      suite.put("parentNodeId", myParentId);
      Map<String, String> startedSuite = new LinkedHashMap<>(suite);
      // The suite of a repetition is no node of the test framework.
      startedSuite.put("origId", "");
      myOut.println(MapSerializerUtil.asString(MapSerializerUtil.TEST_SUITE_STARTED, startedSuite));
      try {
        // Each repetition has its own suite, so it sends its own tree.
        return myTestRun.execute(suiteId, mySendTree);
      }
      finally {
        myOut.println(MapSerializerUtil.asString(MapSerializerUtil.TEST_SUITE_FINISHED, suite));
      }
    }
  }
}
