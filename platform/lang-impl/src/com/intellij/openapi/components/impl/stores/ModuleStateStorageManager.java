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

package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.StateStorageOperation;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

class ModuleStateStorageManager extends StateStorageManagerImpl {
  @NonNls private static final String ROOT_TAG_NAME = "module";
  private final Module myModule;

  public ModuleStateStorageManager(@Nullable final TrackingPathMacroSubstitutor pathMacroManager, final Module module) {
    super(pathMacroManager, ROOT_TAG_NAME, module, module.getPicoContainer());
    myModule = module;
  }

  @Override
  protected StorageData createStorageData(String storageSpec) {
    return new ModuleStoreImpl.ModuleFileData(ROOT_TAG_NAME, myModule);
  }

  @Override
  protected String getOldStorageSpec(Object component, final String componentName, final StateStorageOperation operation) {
    return ModuleStoreImpl.DEFAULT_STATE_STORAGE;
  }

  @Override
  protected String getVersionsFilePath() {
    return PathManager.getConfigPath() + "/componentVersions/" + "module" + getLocationHash() + ".xml";
  }

  private String getLocationHash() {
    return myModule.getName() + Integer.toHexString(myModule.getModuleFilePath().hashCode());    
  }

}
