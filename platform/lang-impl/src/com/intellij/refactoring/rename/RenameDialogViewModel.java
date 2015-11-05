package com.intellij.refactoring.rename;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface RenameDialogViewModel {
  @Nullable
  String[] getSuggestedNames();

  void performRename(@NotNull String name);

  void close(int exitCode);

  void show();
}
