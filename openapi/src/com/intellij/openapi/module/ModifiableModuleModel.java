/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.module;

import com.intellij.openapi.roots.ModuleCircularDependencyException;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.components.LoadCancelledException;
import org.jdom.JDOMException;

import java.io.IOException;

public interface ModifiableModuleModel {
  Module[] getModules();

  Module newModule(String filePath) throws LoadCancelledException;

  Module newModule(String filePath, ModuleType moduleType) throws LoadCancelledException;

  Module loadModule(String filePath) throws InvalidDataException, IOException, JDOMException, ModuleWithNameAlreadyExists, LoadCancelledException;

  void disposeModule(Module module);

  Module findModuleByName(String name);

  void dispose();

  boolean isChanged();

  void commit() throws ModuleCircularDependencyException;

  void commitAssertingNoCircularDependency();

  /**
   * @param module
   * @param newName
   * @throws ModuleWithNameAlreadyExists
   */
  void renameModule(Module module, String newName) throws ModuleWithNameAlreadyExists;

  Module getModuleToBeRenamed(String newName);

  String getNewName(Module module);
}
