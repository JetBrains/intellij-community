// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.util.treeView.AbstractTreeStructureBase;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.List;

public abstract class ProjectAbstractTreeStructureBase extends AbstractTreeStructureBase {
  private List<TreeStructureProvider> myProviders;

  protected ProjectAbstractTreeStructureBase(Project project) {
    super(project);
  }

  @Override
  public @Unmodifiable List<TreeStructureProvider> getProviders() {
    if (myProviders == null) {
      return TreeStructureProvider.EP.getExtensions(myProject);
    }
    return myProviders;
  }

  @TestOnly
  public void setProviders(TreeStructureProvider... treeStructureProviders) {
    myProviders = treeStructureProviders == null ? null : Arrays.asList(treeStructureProviders);
  }
}
