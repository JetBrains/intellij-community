package com.intellij.openapi.wm.ex;

import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.progress.TaskInfo;

public interface StatusBarEx extends StatusBar{
  String getInfo();

  void setPosition(String s);

  void setStatus(String s);

  void setStatusEnabled(boolean enabled);

  void setWriteStatus(boolean locked);

  void clear();

  void updateEditorHighlightingStatus(final boolean isClear);

  void cleanupCustomComponents();

  void add(ProgressIndicatorEx indicator, TaskInfo info);

  boolean isProcessWindowOpen();

  void setProcessWindowOpen(boolean open);
}
