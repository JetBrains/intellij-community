/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.execution.configurations;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vojtech Krasa
 */
public class SearchScopeProvider {
  @NotNull
  public static GlobalSearchScope createSearchScope(@NotNull Project project, @Nullable RunProfile runProfile) {
    Module[] modules = null;
    if (runProfile instanceof SearchScopeProvidingRunProfile) {
      modules = ((SearchScopeProvidingRunProfile)runProfile).getModules();
    }
    if (modules == null || modules.length == 0) {
      return GlobalSearchScope.allScope(project);
    }
    else {
      GlobalSearchScope scope = GlobalSearchScope.moduleRuntimeScope(modules[0], true);
      for (int idx = 1; idx < modules.length; idx++) {
        Module module = modules[idx];
        scope = scope.uniteWith(GlobalSearchScope.moduleRuntimeScope(module, true));
      }
      return scope;
    }
  }
}
