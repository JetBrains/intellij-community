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

import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.StateStorageOperation;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.module.impl.ModuleImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ModuleStateStorageManager extends StateStorageManagerImpl {
  @NonNls private static final String ROOT_TAG_NAME = "module";
  private final ModuleImpl myModule;

  public ModuleStateStorageManager(@NotNull TrackingPathMacroSubstitutor pathMacroManager, @NotNull ModuleImpl module) {
    super(pathMacroManager, ROOT_TAG_NAME, module, module.getPicoContainer());

    myModule = module;
  }

  @NotNull
  @Override
  protected StorageData createStorageData(@NotNull String fileSpec, @NotNull String filePath) {
    return new ModuleStoreImpl.ModuleFileData(ROOT_TAG_NAME, myModule);
  }

  @NotNull
  @Override
  public ExternalizationSession startExternalization() {
    return new StateStorageManagerExternalizationSession() {
      @NotNull
      @Override
      public List<StateStorage.SaveSession> createSaveSessions() {
        if (myModule.getStateStore().getMainStorageData().isDirty()) {
          // force XmlElementStorageSaveSession creation
          getExternalizationSession(myModule.getStateStore().getMainStorage());
        }
        return super.createSaveSessions();
      }
    };
  }

  @Nullable
  @Override
  protected String getOldStorageSpec(@NotNull Object component, @NotNull String componentName, @NotNull StateStorageOperation operation) {
    return StoragePathMacros.MODULE_FILE;
  }

  @NotNull
  @Override
  protected StateStorage.Listener createStorageTopicListener() {
    return myModule.getProject().getMessageBus().syncPublisher(StateStorage.PROJECT_STORAGE_TOPIC);
  }
}