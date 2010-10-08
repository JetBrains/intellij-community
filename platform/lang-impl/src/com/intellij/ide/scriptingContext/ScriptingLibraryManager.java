/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide.scriptingContext;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.LibraryTable;
import org.jetbrains.annotations.Nullable;

/**
 * @author Rustam Vishnyakov
 */
public class ScriptingLibraryManager {

  public static final String WEB_MODULE_TYPE = "WEB_MODULE";

  private ModifiableRootModel myRootModel;
  private Project myProject;

  public ScriptingLibraryManager(Project project) {
    myProject = project;
    myRootModel = getRootModel(project);
  }

  @Nullable
  private static ModifiableRootModel getRootModel(Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (WEB_MODULE_TYPE.equals(module.getModuleType().getId())) {
        return ModuleRootManager.getInstance(module).getModifiableModel();
      }
    }
    return null;
  }

  public void disposeModel() {
    if (myRootModel != null && !myRootModel.isDisposed()) {
      myRootModel.dispose();
      myRootModel = null;
    }
  }

  public void commitModel() {
    if (myRootModel != null && !myRootModel.isDisposed()) {
      myRootModel.commit();
      resetModel();
    }
  }

  public void resetModel() {
    disposeModel();
    myRootModel = getRootModel(myProject);
  }

  @Nullable
  public LibraryTable getLibraryTable() {
    if (myRootModel != null) {
      return myRootModel.getModuleLibraryTable();
    }
    return null;
  }
}
