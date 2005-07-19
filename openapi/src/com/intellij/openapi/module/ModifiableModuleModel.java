/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.module;

import com.intellij.openapi.components.LoadCancelledException;
import com.intellij.openapi.roots.ModuleCircularDependencyException;
import com.intellij.openapi.util.InvalidDataException;
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
