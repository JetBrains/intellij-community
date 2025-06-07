// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.ui;

import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a node in 'Available Elements' tree on 'Output Layout' tab of an artifact editor
 * @see PackagingSourceItemsProvider#getSourceItems
 */
public abstract class PackagingSourceItem {
  private final boolean myProvideElements;

  protected PackagingSourceItem() {
    this(true);
  }

  /**
   * @param provideElements if {@code false} if this item represents a grouping node which doesn't provide packaging elements so its
   * {@link #createElements(ArtifactEditorContext)} method is guaranteed to return the empty list
   */
  protected PackagingSourceItem(boolean provideElements) {
    myProvideElements = provideElements;
  }

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();

  public abstract @NotNull SourceItemPresentation createPresentation(@NotNull ArtifactEditorContext context);

  public abstract @NotNull List<? extends PackagingElement<?>> createElements(@NotNull ArtifactEditorContext context);

  public boolean isProvideElements() {
    return myProvideElements;
  }

  public @NotNull PackagingElementOutputKind getKindOfProducedElements() {
    return PackagingElementOutputKind.OTHER;
  }
}
