/*
 * User: anna
 * Date: 25-May-2007
 */
package com.intellij.execution.testframework;

import com.intellij.openapi.localVcs.LvcsLabel;
import com.intellij.openapi.localVcs.LocalVcs;
import com.intellij.execution.ExecutionBundle;
import com.intellij.rt.execution.junit.states.PoolOfTestStates;

public class LvcsHelper {
  private LvcsHelper() {
  }

  public static void addLabel(final TestFrameworkRunningModel model) {
    String label;
    final byte labelType;
    if (model.getRoot().isDefect()) {
      labelType = LvcsLabel.TYPE_TESTS_FAILED;
      label = ExecutionBundle.message("junit.runing.info.tests.failed.label");
    } else {
      labelType = LvcsLabel.TYPE_TESTS_SUCCESSFUL;
      if (model.getRoot().getMagnitude() != PoolOfTestStates.PASSED_INDEX){
        label = ExecutionBundle.message("tests.passed.with.warnings.message");
      }
      else {
        label = ExecutionBundle.message("junit.runing.info.tests.passed.label");
      }
    }
    final TestConsoleProperties consoleProperties = model.getProperties();
    LocalVcs.getInstance(consoleProperties.getProject()).addLabel(labelType, label + " " + consoleProperties.getConfiguration().getName(), "");
  }
}