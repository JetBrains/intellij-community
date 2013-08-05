/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.project;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Disposer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** @deprecated use {@link ModuleListener#modulesRenamed(Project, java.util.List, com.intellij.util.Function)} (to remove in IDEA 14) */
@SuppressWarnings("UnusedDeclaration")
public abstract class ModuleNameTracker extends ModuleAdapter {
  private final Map<Module, String> myModulesNames = new HashMap<Module, String>();
  private final Project myProject;

  public ModuleNameTracker(Project project) {
    myProject = project;
    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        myModulesNames.clear();
      }
    });
  }

  @Override
  public void moduleAdded(final Project project, final Module module) {
    if (myProject == project) {
      myModulesNames.put(module, module.getName());
    }
  }

  @Override
  public void moduleRemoved(final Project project, final Module module) {
    if (myProject == project) {
      myModulesNames.remove(module);
    }
  }

  @Override
  public void modulesRenamed(final Project project, final List<Module> modules) {
    if (myProject != project) {
      return;
    }

    Map<String, String> old2newNames = new HashMap<String, String>(modules.size());
    for (Module module : modules) {
      String newName = module.getName();
      String oldName = myModulesNames.put(module, newName);
      old2newNames.put(oldName, newName);
    }
    modulesRenamed(project, old2newNames);
  }

  protected abstract void modulesRenamed(final Project project, final Map<String, String> old2newNames);
}
