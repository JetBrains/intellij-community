/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.module;

import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleCircularDependencyException;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.components.LoadCancelledException;
import com.intellij.util.graph.Graph;
import org.jdom.JDOMException;

import java.io.IOException;
import java.util.Comparator;

public abstract class ModuleManager {
  public static ModuleManager getInstance(Project project) {
    return project.getComponent(ModuleManager.class);
  }

  public abstract Module newModule(String filePath) throws LoadCancelledException;

  public abstract Module newModule(String filePath, ModuleType moduleType) throws LoadCancelledException;

  public abstract Module loadModule(String filePath) throws InvalidDataException, IOException, JDOMException, ModuleWithNameAlreadyExists, ModuleCircularDependencyException, LoadCancelledException;

  public abstract void disposeModule(Module module);

  public abstract Module[] getModules();

  public abstract Module findModuleByName(String name);

  public abstract Module[] getSortedModules();

  public abstract Comparator<Module> moduleDependencyComparator();

  /**
   * Returns array of <i>modules that depend on</i> given module.
   * @param module
   * @return
   */
  public abstract Module[] getModuleDependentModules(Module module);

  public abstract boolean isModuleDependent(Module module, Module onModule);

  public abstract void addModuleListener(ModuleListener listener);

  public abstract void removeModuleListener(ModuleListener listener);

  public abstract Graph<Module> moduleGraph();

  public abstract ModifiableModuleModel getModifiableModel();

  public abstract void dispatchPendingEvent(ModuleListener listener);

  public abstract String getModuleGroup(Module module);
}
