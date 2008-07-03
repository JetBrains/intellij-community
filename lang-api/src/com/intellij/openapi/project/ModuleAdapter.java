/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.project;

import com.intellij.openapi.module.Module;

import java.util.List;

/**
 * @author nik
 */
public abstract class ModuleAdapter implements ModuleListener {

  public void moduleAdded(Project project, Module module) {
  }

  public void beforeModuleRemoved(Project project, Module module) {
  }

  public void moduleRemoved(Project project, Module module) {
  }

  public void modulesRenamed(Project project, List<Module> modules) {
  }
}
