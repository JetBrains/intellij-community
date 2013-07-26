package com.intellij.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface ColoredTextContainer {
  void append(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes);
  void setIcon(@Nullable final Icon icon);

  void setToolTipText(String text);
}