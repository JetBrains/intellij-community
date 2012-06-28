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

/**
 * @author cdr
 */
package com.intellij.openapi.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ModuleUtil extends ModuleUtilCore {

  private ModuleUtil() {}

  @Nullable
  public static Module getParentModuleOfType(ModuleType expectedModuleType, Module module) {
    if (module == null) return null;
    if (expectedModuleType.equals(ModuleType.get(module))) return module;
    final List<Module> parents = getParentModulesOfType(expectedModuleType, module);
    return parents.isEmpty() ? null : parents.get(0);
  }

  @NotNull
  public static List<Module> getParentModulesOfType(ModuleType expectedModuleType, Module module) {
    final List<Module> parents = ModuleManager.getInstance(module.getProject()).getModuleDependentModules(module);
    ArrayList<Module> modules = new ArrayList<Module>();
    for (Module parent : parents) {
      if (expectedModuleType.equals(ModuleType.get(parent))) {
        modules.add(parent);
      }
    }
    return modules;
  }
}
