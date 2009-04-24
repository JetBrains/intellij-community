package com.intellij.packaging.ui;

import com.intellij.packaging.elements.PackagingElement;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class PackagingSourceItem {

  public abstract void render(@NotNull ColoredTreeCellRenderer renderer);

  @NotNull
  public abstract PackagingElement createElement();

}
