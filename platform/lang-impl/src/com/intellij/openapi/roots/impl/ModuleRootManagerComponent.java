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
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.components.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.impl.storage.ClassPathStorageUtil;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;

/**
 * @author yole
 */
@State(
  name = "NewModuleRootManager",
  storages = {
    @Storage(
      id = ClassPathStorageUtil.DEFAULT_STORAGE,
      file = "$MODULE_FILE$"
    ),

    @Storage(
          id = ClasspathStorage.SPECIAL_STORAGE,
          storageClass = ClasspathStorage.class
    )
  },
  storageChooser = ModuleRootManagerComponent.StorageChooser.class
)
public class ModuleRootManagerComponent extends ModuleRootManagerImpl implements
                                                                      PersistentStateComponent<ModuleRootManagerImpl.ModuleRootManagerState> {
  public ModuleRootManagerComponent(Module module,
                                    DirectoryIndex directoryIndex,
                                    ProjectRootManagerImpl projectRootManager,
                                    VirtualFilePointerManager filePointerManager) {
    super(module, directoryIndex, projectRootManager, filePointerManager);
  }

  public static class StorageChooser implements StateStorageChooser<ModuleRootManagerImpl> {
    @Override
    public Storage[] selectStorages(Storage[] storages, ModuleRootManagerImpl moduleRootManager, final StateStorageOperation operation) {
      final boolean isDefaultStorageType = ClassPathStorageUtil.isDefaultStorage(moduleRootManager.getModule());
      final String id = isDefaultStorageType ? ClassPathStorageUtil.DEFAULT_STORAGE: ClasspathStorage.SPECIAL_STORAGE;
      for (Storage storage : storages) {
        if (storage.id().equals(id)) return new Storage[]{storage};
      }
      throw new IllegalArgumentException();
    }
  }
}
