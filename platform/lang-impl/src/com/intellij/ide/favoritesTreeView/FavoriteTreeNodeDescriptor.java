// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@Deprecated(forRemoval = true)
public final class FavoriteTreeNodeDescriptor extends PresentableNodeDescriptor<AbstractTreeNode<?>> {
  private final AbstractTreeNode<?> myElement;
  public static final FavoriteTreeNodeDescriptor[] EMPTY_ARRAY = new FavoriteTreeNodeDescriptor[0];

  public FavoriteTreeNodeDescriptor(@NotNull Project project, final NodeDescriptor parentDescriptor, final AbstractTreeNode element) {
    super(project, parentDescriptor);

    myElement = element;
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    myElement.update();
    presentation.copyFrom(myElement.getPresentation());
  }

  @Override
  public AbstractTreeNode getElement() {
    return myElement;
  }

  public boolean equals(Object object) {
    if (!(object instanceof FavoriteTreeNodeDescriptor)) return false;
    return ((FavoriteTreeNodeDescriptor)object).getElement().equals(myElement);
  }

  public int hashCode() {
    return myElement.hashCode();
  }

  @Override
  public PresentableNodeDescriptor getChildToHighlightAt(int index) {
    return myElement.getChildToHighlightAt(index);
  }
}
