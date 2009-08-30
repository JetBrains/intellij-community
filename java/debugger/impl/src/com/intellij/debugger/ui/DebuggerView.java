package com.intellij.debugger.ui;

public interface DebuggerView {

  void setUpdateEnabled(boolean enabled);

  boolean isRefreshNeeded();
  void rebuildIfVisible(final int eventContext);

}
