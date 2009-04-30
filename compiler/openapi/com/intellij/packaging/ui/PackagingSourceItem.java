package com.intellij.packaging.ui;

import com.intellij.packaging.elements.PackagingElement;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public abstract class PackagingSourceItem extends PackagingSourceItemsGroup {

  public abstract void render(@NotNull ColoredTreeCellRenderer renderer);

  @NotNull
  public abstract List<? extends PackagingElement<?>> createElements();

}
