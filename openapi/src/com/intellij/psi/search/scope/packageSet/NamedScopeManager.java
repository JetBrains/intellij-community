/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;

public class NamedScopeManager extends NamedScopesHolder implements ProjectComponent {
  public static NamedScopeManager getInstance(Project project) {
    return project.getComponent(NamedScopeManager.class);
  }

  public NamedScopeManager() {}

  public String getComponentName() {
    return "NamedScopeManager";
  }

  public void initComponent() {}
  public void disposeComponent() {}
  public void projectOpened() {}
  public void projectClosed() {}
}