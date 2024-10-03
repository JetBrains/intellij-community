package com.intellij.execution.ui.layout.actions;

import com.intellij.ui.content.Content;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface ViewLayoutModificationAction {
  @NotNull
  Content getContent();
}
