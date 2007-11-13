package com.intellij.openapi.wm.ex;

import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.wm.StatusBar;

import javax.swing.*;

public interface StatusBarEx extends StatusBar{
  String getInfo();

  void setPosition(String s);

  void setStatus(String s);

  void setStatusEnabled(boolean enabled);

  void setWriteStatus(boolean locked);

  void clear();

  void addFileStatusComponent(JComponent component, final Runnable onStatusChange);

  void updateFileStatusComponents();

  void removeFileStatusComponent(final JComponent component);

  void cleanupCustomComponents();

  void add(ProgressIndicatorEx indicator, TaskInfo info);

  boolean isProcessWindowOpen();

  void setProcessWindowOpen(boolean open);
}
