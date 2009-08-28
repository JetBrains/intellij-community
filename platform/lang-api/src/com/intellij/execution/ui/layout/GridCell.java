package com.intellij.execution.ui.layout;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.content.Content;

public interface GridCell {
  boolean isDetached();

  int getContentCount();

  void attach();

  void minimize(final Content content);

  ActionCallback detach();
}