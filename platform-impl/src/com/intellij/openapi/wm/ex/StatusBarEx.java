package com.intellij.openapi.wm.ex;

import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.impl.status.StatusBarPatch;
import com.intellij.openapi.editor.Editor;

public interface StatusBarEx extends StatusBar{
  String getInfo();

  void clear();

  void addFileStatusComponent(StatusBarPatch component);

  void removeFileStatusComponent(StatusBarPatch component);

  void cleanupCustomComponents();

  void add(ProgressIndicatorEx indicator, TaskInfo info);

  boolean isProcessWindowOpen();

  void setProcessWindowOpen(boolean open);

  void update(final Editor editor);
  void somethingChanged();
}
