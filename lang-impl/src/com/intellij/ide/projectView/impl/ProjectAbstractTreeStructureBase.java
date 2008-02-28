package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.util.treeView.AbstractTreeStructureBase;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;

import java.util.Arrays;
import java.util.List;

public abstract class ProjectAbstractTreeStructureBase extends AbstractTreeStructureBase {
  private List<TreeStructureProvider> myProviders;

  protected ProjectAbstractTreeStructureBase(Project project) {
    super(project);
  }

  public List<TreeStructureProvider> getProviders() {
    if (myProviders == null) {
      final TreeStructureProvider[] providers = Extensions.getExtensions(TreeStructureProvider.EP_NAME, myProject);
      myProviders = Arrays.asList(providers);
    }
    return myProviders;
  }

  public void setProviders(TreeStructureProvider... treeStructureProviders) {
    myProviders = treeStructureProviders == null ? null : Arrays.asList(treeStructureProviders);
  }
}
