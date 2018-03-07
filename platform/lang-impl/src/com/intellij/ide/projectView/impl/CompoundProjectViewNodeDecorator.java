// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;

public class CompoundProjectViewNodeDecorator implements ProjectViewNodeDecorator {
  private final Project project;

  public CompoundProjectViewNodeDecorator(@NotNull Project project) {
    this.project = project;
  }

  @Override
  public void decorate(ProjectViewNode node, PresentationData data) {
    if (!project.isDisposed()) {
      for (ProjectViewNodeDecorator decorator : EP_NAME.getExtensions(project)) {
        decorator.decorate(node, data);
      }
    }
  }

  @Override
  public void decorate(PackageDependenciesNode node, ColoredTreeCellRenderer cellRenderer) {
    if (!project.isDisposed()) {
      for (ProjectViewNodeDecorator decorator : EP_NAME.getExtensions(project)) {
        decorator.decorate(node, cellRenderer);
      }
    }
  }
}
