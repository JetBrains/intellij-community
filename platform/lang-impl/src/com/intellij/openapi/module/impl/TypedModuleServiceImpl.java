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
package com.intellij.openapi.module.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.TypedModuleService;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author nik
 */
public class TypedModuleServiceImpl extends TypedModuleService {
  private MultiMap<ModuleType<?>, Module> myCachedModules;
  private final ModuleManager myModuleManager;

  public TypedModuleServiceImpl(ModuleManager moduleManager, MessageBus messageBus) {
    myModuleManager = moduleManager;
    messageBus.connect().subscribe(ProjectTopics.MODULES, new ModuleAdapter() {
      @Override
      public void moduleAdded(Project project, Module module) {
        myCachedModules = null;
      }

      @Override
      public void moduleRemoved(Project project, Module module) {
        myCachedModules = null;
      }
    });
  }

  private MultiMap<ModuleType<?>, Module> getCachedModules() {
    if (myCachedModules == null) {
      myCachedModules = new MultiMap<ModuleType<?>, Module>();
      for (Module module : myModuleManager.getModules()) {
        myCachedModules.putValue(ModuleType.get(module), module);
      }
    }
    return myCachedModules;
  }

  @NotNull
  @Override
  public Collection<Module> getModulesOfType(@NotNull ModuleType<?> moduleType) {
    return getCachedModules().get(moduleType);
  }

  @Override
  public boolean hasModulesOfType(@NotNull ModuleType<?> module) {
    return !getModulesOfType(module).isEmpty();
  }
}
