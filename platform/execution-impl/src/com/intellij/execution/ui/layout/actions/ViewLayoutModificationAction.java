package com.intellij.execution.ui.layout.actions;

import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;

public interface ViewLayoutModificationAction {
  @NotNull
  Content getContent();
}
