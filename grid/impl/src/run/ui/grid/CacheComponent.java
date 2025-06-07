package com.intellij.database.run.ui.grid;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface CacheComponent {
  @NotNull JComponent getComponent();
  @Nullable JScrollBar getVerticalScrollBar();
  @Nullable JScrollBar getHorizontalScrollBar();
}
