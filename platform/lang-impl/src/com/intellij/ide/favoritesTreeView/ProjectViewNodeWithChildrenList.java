// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Deprecated(forRemoval = true)
public abstract class ProjectViewNodeWithChildrenList<T> extends ProjectViewNode<T> {
  protected final List<AbstractTreeNode<?>> myChildren;

  protected ProjectViewNodeWithChildrenList(Project project, @NotNull T t, ViewSettings viewSettings) {
    super(project, t, viewSettings);

    myChildren = new ArrayList<>();
  }

  @Override
  public @NotNull Collection<? extends AbstractTreeNode<?>> getChildren() {
    return myChildren;
  }

  public void addChild(final AbstractTreeNode<?> node) {
    myChildren.add(node);
    node.setParent(this);
  }
}
