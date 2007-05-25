/*
 * User: anna
 * Date: 25-May-2007
 */
package com.intellij.execution.testframework;

import com.intellij.openapi.Disposable;

import javax.swing.*;

public interface TestFrameworkRunningModel extends Disposable {
  TestConsoleProperties getProperties();

  void setFilter(final Filter filter);

  void addListener(ModelListener l);

  boolean isRunning();

  JTree getTreeView();

  boolean hasTestSuites();

  interface ModelListener {
    void onDispose(final TestFrameworkRunningModel model);
  }
}