package com.intellij.openapi.util.diff;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface DiffRequestPanel extends Disposable {
  void setRequest(@Nullable DiffRequest request);

  @NotNull
  JComponent getComponent();

  @Nullable
  JComponent getPreferredFocusedComponent();

  <T> void putContextHints(@NotNull Key<T> key, @Nullable T value);
}
