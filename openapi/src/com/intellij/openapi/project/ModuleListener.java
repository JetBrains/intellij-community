/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.project;

import com.intellij.openapi.module.Module;

import java.util.EventListener;
import java.util.List;

/**
 * @author max
 */
public interface ModuleListener extends EventListener {
  void moduleAdded(Project project, Module module);

  void beforeModuleRemoved(Project project, Module module);

  void moduleRemoved(Project project, Module module);

  void modulesRenamed(Project project, List<Module> modules);
}
