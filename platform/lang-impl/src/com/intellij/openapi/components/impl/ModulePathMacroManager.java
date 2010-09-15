/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.PathMacroMap;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class ModulePathMacroManager extends BasePathMacroManager {
  private final Module myModule;

  public ModulePathMacroManager(final Module module) {
    myModule = module;
  }

  public ExpandMacroToPathMap getExpandMacroMap() {
    ExpandMacroToPathMap result = new ExpandMacroToPathMap();
    getExpandModuleHomeReplacements(result);
    result.putAll(super.getExpandMacroMap());
    return result;
  }

  public ReplacePathToMacroMap getReplacePathMap() {
    ReplacePathToMacroMap result = super.getReplacePathMap();
    if (!myModule.isDisposed()) {
      String projectDir = ProjectPathMacroManager.getProjectDir(myModule.getProject());
      if (projectDir != null) {
        projectDir = projectDir.replace('/', File.separatorChar);
      }
      String stopAt = projectDir;

      final String userHome = getUserHome();
      if (stopAt != null && stopAt.startsWith(userHome)) {
        stopAt = userHome;
      }

      addFileHierarchyReplacements(result, PathMacrosImpl.MODULE_DIR_MACRO_NAME, getModuleDir(myModule.getModuleFilePath()), stopAt);
    }
    return result;
  }

  private void getExpandModuleHomeReplacements(ExpandMacroToPathMap result) {
    String moduleDir = myModule.isDisposed() ? null : getModuleDir(myModule.getModuleFilePath());
    if (moduleDir == null) return;

    File f = new File(moduleDir.replace('/', File.separatorChar));

    getExpandModuleHomeReplacements(result, f, "$" + PathMacrosImpl.MODULE_DIR_MACRO_NAME + "$");
  }

  private static void getExpandModuleHomeReplacements(ExpandMacroToPathMap result, File f, String macro) {
    if (f == null) return;

    getExpandModuleHomeReplacements(result, f.getParentFile(), macro + "/..");
    String path = PathMacroMap.quotePath(f.getAbsolutePath());
    String s = macro;

    if (StringUtil.endsWithChar(path, '/')) s += "/";

    result.put(s, path);
  }

  @Nullable
  private String getModuleDir(String moduleFilePath) {
    File moduleDirFile = new File(moduleFilePath).getParentFile();
    if (moduleDirFile == null) return null;

    // hack so that, if a module is stored inside the .idea directory, the base directory
    // rather than the .idea directory itself is considered the module root
    // (so that a Ruby IDE project doesn't break if its directory is moved together with the .idea directory)
    final VirtualFile baseDir = myModule.getProject().getBaseDir();
    if (baseDir != null) {
      File moduleDirParent = moduleDirFile.getParentFile();
      if (moduleDirParent != null && moduleDirFile.getName().equals(".idea") &&
          moduleDirParent.getPath().equals(FileUtil.toSystemDependentName(baseDir.getPath()))) {
        moduleDirFile = moduleDirParent;
      }
    }
    String moduleDir = moduleDirFile.getPath();
    moduleDir = moduleDir.replace(File.separatorChar, '/');
    if (moduleDir.endsWith(":/")) {
      moduleDir = moduleDir.substring(0, moduleDir.length() - 1);
    }
    return moduleDir;
  }

}
