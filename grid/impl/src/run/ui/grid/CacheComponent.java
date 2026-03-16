package com.intellij.database.run.ui.grid;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JScrollBar;

public interface CacheComponent {
  @NotNull JComponent getComponent();
  @Nullable JScrollBar getVerticalScrollBar();
  @Nullable JScrollBar getHorizontalScrollBar();
}
