/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.module;

import com.intellij.openapi.project.Project;

public abstract class ModulePointerManager {
  public static ModulePointerManager getInstance(Project project) {
    return project.getComponent(ModulePointerManager.class);
  }

  public abstract ModulePointer create(Module module);
  public abstract ModulePointer create(String moduleName);
}
