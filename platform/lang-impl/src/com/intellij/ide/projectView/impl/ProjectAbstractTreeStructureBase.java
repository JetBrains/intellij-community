// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.util.treeView.AbstractTreeStructureBase;
import com.intellij.openapi.project.Project;

import java.util.Arrays;
import java.util.List;

public abstract class ProjectAbstractTreeStructureBase extends AbstractTreeStructureBase {
  private List<TreeStructureProvider> myProviders;

  protected ProjectAbstractTreeStructureBase(Project project) {
    super(project);
  }

  @Override
  public List<TreeStructureProvider> getProviders() {
    if (myProviders == null) {
      final TreeStructureProvider[] providers = TreeStructureProvider.EP_NAME.getExtensions(myProject);
      myProviders = Arrays.asList(providers);
    }
    return myProviders;
  }

  public void setProviders(TreeStructureProvider... treeStructureProviders) {
    myProviders = treeStructureProviders == null ? null : Arrays.asList(treeStructureProviders);
  }
}
