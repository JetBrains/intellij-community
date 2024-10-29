// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.packageDependencies.ui;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiFile;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Set;

public final class ModuleGroupNode extends PackageDependenciesNode {
  private final ModuleGroup myModuleGroup;

  public ModuleGroupNode(ModuleGroup moduleGroup, Project project) {
    super(project);
    myModuleGroup = moduleGroup;
  }

  @Override
  public void fillFiles(Set<? super PsiFile> set, boolean recursively) {
    super.fillFiles(set, recursively);
    int count = getChildCount();
    for (int i = 0; i < count; i++) {
      PackageDependenciesNode child = (PackageDependenciesNode)getChildAt(i);
      child.fillFiles(set, true);
    }
  }

  @Override
  public Icon getIcon() {
    return PlatformIcons.CLOSED_MODULE_GROUP_ICON;
  }

  public String toString() {
    return myModuleGroup == null ? CodeInsightBundle.message("unknown.node.text") : myModuleGroup.toString();
  }

  public String getModuleGroupName() {
    return myModuleGroup.presentableText();
  }

  public ModuleGroup getModuleGroup() {
    return myModuleGroup;
  }

  public boolean equals(Object o) {
    if (isEquals()){
      return super.equals(o);
    }
    if (this == o) return true;
    if (!(o instanceof ModuleGroupNode moduleNode)) return false;

    return Comparing.equal(myModuleGroup, moduleNode.myModuleGroup);
  }

  public int hashCode() {
    return myModuleGroup == null ? 0 : myModuleGroup.hashCode();
  }

  public @NotNull Project getProject() {
    return myProject;
  }
}
