package com.intellij.packaging.ui;

import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public abstract class PackagingSourceItem {
  private boolean myProvideElements;

  protected PackagingSourceItem() {
    this(true);
  }

  protected PackagingSourceItem(boolean provideElements) {
    myProvideElements = provideElements;
  }

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();

  public abstract SourceItemPresentation createPresentation(@NotNull PackagingEditorContext context);

  @NotNull
  public abstract List<? extends PackagingElement<?>> createElements(@NotNull PackagingEditorContext context);

  public boolean isProvideElements() {
    return myProvideElements;
  }

  @NotNull
  public PackagingElementOutputKind getKindOfProducedElements() {
    return PackagingElementOutputKind.OTHER;
  }
}
