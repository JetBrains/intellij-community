// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.ide.impl;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.projectView.impl.ProjectTreeStructure;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.ide.projectView.impl.nodes.StructureViewModuleNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class ModuleStructurePane extends ProjectViewPane {
  private final @NotNull Module myModule;

  public ModuleStructurePane(@NotNull Module module) {
    super(module.getProject());
    myModule = module;
  }

  @Override
  protected @NotNull ProjectAbstractTreeStructureBase createStructure() {
    return new ProjectTreeStructure(myProject, ID){
      @Override
      protected AbstractTreeNode createRoot(final @NotNull Project project, @NotNull ViewSettings settings) {
        return new StructureViewModuleNode(project, myModule, settings);
      }
    };
  }
}
