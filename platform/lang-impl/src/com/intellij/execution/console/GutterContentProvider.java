package com.intellij.execution.console;

import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GutterContentProvider {
  public void beforeUiComponentUpdate(@NotNull Editor editor) {
  }

  public void documentCleared(@NotNull Editor editor) {
  }

  @Nullable
  public abstract String getText(int line, @NotNull Editor editor);

  @Nullable
  public abstract String getToolTip(int line, @NotNull Editor editor);

  public abstract void doAction(int line, @NotNull Editor editor);
}