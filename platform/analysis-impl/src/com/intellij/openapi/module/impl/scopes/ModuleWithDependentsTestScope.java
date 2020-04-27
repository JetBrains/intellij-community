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
package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import org.jetbrains.annotations.NotNull;

// Tests only (module plus dependencies) scope
// Delegates to ModuleWithDependentsScope with extra flag testOnly to reduce memory for holding modules and CPU for traversing dependencies.
class ModuleWithDependentsTestScope extends DelegatingGlobalSearchScope {
  ModuleWithDependentsTestScope(@NotNull Module module) {
    // the additional equality argument allows to distinguish ModuleWithDependentsTestScope from ModuleWithDependentsScope
    super(new ModuleWithDependentsScope(module), true);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return getBaseScope().contains(file, true);
  }

  @NotNull
  ModuleWithDependentsScope getBaseScope() {
    return (ModuleWithDependentsScope)myBaseScope;
  }
}
