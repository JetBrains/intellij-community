/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.plugins;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.platform.loader.repository.RuntimeModuleId;

import java.util.Set;

/**
 * @author nik
 */
public class PluginRuntimeModule {
  private final RuntimeModuleId myModuleId;
  private final Set<PluginModuleScope> myScopes;

  public PluginRuntimeModule(RuntimeModuleId moduleId, Set<PluginModuleScope> scopes) {
    myModuleId = moduleId;
    myScopes = scopes;
  }

  @NotNull
  public RuntimeModuleId getModuleId() {
    return myModuleId;
  }

  public boolean isInScope(@NotNull PluginModuleScope scope) {
    return myScopes.contains(scope);
  }
}
