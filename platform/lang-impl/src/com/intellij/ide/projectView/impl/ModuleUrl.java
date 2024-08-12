// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.projectView.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;

public final class ModuleUrl extends AbstractUrl {
  private static final @NonNls String ELEMENT_TYPE = "module";

  public ModuleUrl(String url, String moduleName) {
    super(url, moduleName, ELEMENT_TYPE);
  }

  @Override
  public Object[] createPath(Project project) {
    final Module module = moduleName != null ? ModuleManager.getInstance(project).findModuleByName(moduleName) : null;
    if (module == null) return null;
    return new Object[]{module};
  }

  @Override
  protected AbstractUrl createUrl(String moduleName, String url) {
      return new ModuleUrl(url, moduleName);
  }

  @Override
  public AbstractUrl createUrlByElement(Object element) {
    if (element instanceof Module module) {
      return new ModuleUrl("", module.getName());
    }
    return null;
  }
}
