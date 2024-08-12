// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractProjectTreeStructure extends ProjectAbstractTreeStructureBase implements ViewSettings {
  private final AbstractTreeNode myRoot;

  public AbstractProjectTreeStructure(@NotNull Project project) {
    super(project);
    myRoot = createRoot(project, this);
  }

  protected AbstractTreeNode createRoot(@NotNull Project project, @NotNull ViewSettings settings) {
    return new ProjectViewProjectNode(myProject, this);
  }

  @Override
  public final @NotNull Object getRootElement() {
    return myRoot;
  }

  @Override
  public final void commit() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
  }

  @Override
  public @NotNull ActionCallback asyncCommit() {
    return asyncCommitDocuments(myProject);
  }

  @Override
  public final boolean hasSomethingToCommit() {
    return !myProject.isDisposed()
           && PsiDocumentManager.getInstance(myProject).hasUncommitedDocuments();
  }

  @Override
  public boolean isAlwaysLeaf(@NotNull Object element) {
    if (element instanceof ProjectViewNode) {
      return ((ProjectViewNode<?>)element).isAlwaysLeaf();
    }
    return super.isAlwaysLeaf(element);
  }
}