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
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

public class ModuleStoreImpl extends BaseFileConfigurableStoreImpl {
  private final ModuleImpl myModule;

  public ModuleStoreImpl(@NotNull ModuleImpl module, @NotNull PathMacroManager pathMacroManager) {
    super(pathMacroManager);

    myModule = module;
  }

  @NotNull
  public FileBasedStorage getMainStorage() {
    FileBasedStorage storage = (FileBasedStorage)getStateStorageManager().getStateStorage(StoragePathMacros.MODULE_FILE, RoamingType.PER_USER);
    assert storage != null;
    return storage;
  }

  @Override
  protected Project getProject() {
    return myModule.getProject();
  }

  @Override
  protected boolean optimizeTestLoading() {
    return ((ProjectEx)myModule.getProject()).isOptimiseTestLoadSpeed();
  }

  @NotNull
  @Override
  protected MessageBus getMessageBus() {
    return myModule.getMessageBus();
  }

  @NotNull
  @Override
  protected StateStorageManager createStateStorageManager() {
    return new ModuleStateStorageManager(myPathMacroManager.createTrackingSubstitutor(), myModule);
  }
}
