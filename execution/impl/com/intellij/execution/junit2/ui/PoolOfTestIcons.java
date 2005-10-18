package com.intellij.execution.junit2.ui;

import javax.swing.*;

public interface PoolOfTestIcons {
  Icon SKIPPED_ICON = TestsUIUtil.loadIcon("testSkipped");
  Icon PASSED_ICON = TestsUIUtil.loadIcon("testPassed");
  Icon FAILED_ICON = TestsUIUtil.loadIcon("testFailed");
  Icon ERROR_ICON = TestsUIUtil.loadIcon("testError");
  Icon NOT_RAN = TestsUIUtil.loadIcon("testNotRan");
  Icon LOADING_ICON = TestsUIUtil.loadIcon("loadingTree");
  Icon TERMINATED_ICON = TestsUIUtil.loadIcon("testTerminated");
}
