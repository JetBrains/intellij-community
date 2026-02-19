// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInManager;
import com.intellij.ide.StandardTargetWeights;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

public final class ProjectPaneSelectInTarget extends ProjectViewSelectInTarget implements DumbAware {
  public ProjectPaneSelectInTarget(Project project) {
    super(project);
  }

  @Override
  public String toString() {
    return SelectInManager.getProject();
  }

  @Override
  public boolean isSubIdSelectable(String subId, SelectInContext context) {
    return canSelect(context);
  }

  @Override
  public String getMinorViewId() {
    return ProjectViewPane.ID;
  }

  @Override
  public float getWeight() {
    return StandardTargetWeights.PROJECT_WEIGHT;
  }

}
