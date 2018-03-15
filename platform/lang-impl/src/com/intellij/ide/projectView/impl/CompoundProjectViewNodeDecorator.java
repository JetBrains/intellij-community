// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;

/**
 * This class is intended to combine all decorators for batch usages.
 *
 * @author Sergey Malenkov
 */
public final class CompoundProjectViewNodeDecorator implements ProjectViewNodeDecorator {
  private static final Key<ProjectViewNodeDecorator> KEY = Key.create("ProjectViewNodeDecorator");
  private final Project project;

  /**
   * @return a shared instance for the specified project
   */
  @NotNull
  public static ProjectViewNodeDecorator get(@NotNull Project project) {
    ProjectViewNodeDecorator provider = project.getUserData(KEY);
    if (provider != null) return provider;
    provider = new CompoundProjectViewNodeDecorator(project);
    project.putUserData(KEY, provider);
    return provider;
  }

  private CompoundProjectViewNodeDecorator(@NotNull Project project) {
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
