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

  public List<TreeStructureProvider> getProviders() {
    if (myProviders == null) {
      myProviders = (List<TreeStructureProvider>)myProject.getPicoContainer().getComponentInstancesOfType(TreeStructureProvider.class);
    }
    return myProviders;
  }

  public void setProviders(TreeStructureProvider... treeStructureProviders) {
    myProviders = treeStructureProviders == null ? null : Arrays.asList(treeStructureProviders);
  }
}
