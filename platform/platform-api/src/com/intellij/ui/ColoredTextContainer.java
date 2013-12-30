package com.intellij.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface ColoredTextContainer {
  void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes);

  void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes, Object tag);

  void setIcon(@Nullable Icon icon);

  void setToolTipText(@Nullable String text);
}