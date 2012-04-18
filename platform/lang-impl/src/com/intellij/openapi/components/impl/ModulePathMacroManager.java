/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.openapi.components.impl;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class ModulePathMacroManager extends BasePathMacroManager {
  private final Module myModule;

  public ModulePathMacroManager(PathMacros pathMacros, Module module) {
    super(pathMacros);
    myModule = module;
  }

  @Override
  public ExpandMacroToPathMap getExpandMacroMap() {
    final ExpandMacroToPathMap result = new ExpandMacroToPathMap();

    if (!myModule.isDisposed()) {
      addFileHierarchyReplacements(result, PathMacrosImpl.MODULE_DIR_MACRO_NAME, getModuleDir(myModule.getModuleFilePath()));
    }

    result.putAll(super.getExpandMacroMap());

    return result;
  }

  @Override
  public ReplacePathToMacroMap getReplacePathMap() {
    final ReplacePathToMacroMap result = super.getReplacePathMap();

    if (!myModule.isDisposed()) {
      final String modulePath = getModuleDir(myModule.getModuleFilePath());
      addFileHierarchyReplacements(result, PathMacrosImpl.MODULE_DIR_MACRO_NAME, modulePath, PathMacrosImpl.getUserHome());
    }

    return result;
  }

  @Nullable
  private static String getModuleDir(String moduleFilePath) {
    File moduleDirFile = new File(moduleFilePath).getParentFile();
    if (moduleDirFile == null) return null;

    // hack so that, if a module is stored inside the .idea directory, the base directory
    // rather than the .idea directory itself is considered the module root
    // (so that a Ruby IDE project doesn't break if its directory is moved together with the .idea directory)
    File moduleDirParent = moduleDirFile.getParentFile();
    if (moduleDirParent != null && moduleDirFile.getName().equals(".idea")) {
      moduleDirFile = moduleDirParent;
    }
    String moduleDir = moduleDirFile.getPath();
    moduleDir = moduleDir.replace(File.separatorChar, '/');
    if (moduleDir.endsWith(":/")) {
      moduleDir = moduleDir.substring(0, moduleDir.length() - 1);
    }
    return moduleDir;
  }
}
