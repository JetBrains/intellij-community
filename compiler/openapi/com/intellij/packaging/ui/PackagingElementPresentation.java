package com.intellij.packaging.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.ui.ColoredTreeCellRenderer;

/**
 * @author nik
 */
public abstract class PackagingElementPresentation {

  public abstract String getPresentableName();

  public String getSearchName() {
    return getPresentableName();
  }

  public abstract void render(@NotNull ColoredTreeCellRenderer renderer);

  @Nullable
  public String getTooltipText() {
    return null;
  }

  public double getWeight() {
    return PackagingElementWeights.FILE;
  }
}
