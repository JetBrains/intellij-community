/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
@State(
  name = "NewModuleRootManager",
  storages = {
    @Storage(StoragePathMacros.MODULE_FILE),
    @Storage(storageClass = ClasspathStorage.class)
  }
)
public class ModuleRootManagerComponent extends ModuleRootManagerImpl implements
                                                                      PersistentStateComponent<ModuleRootManagerImpl.ModuleRootManagerState>,
                                                                      StateStorageChooserEx {
  public ModuleRootManagerComponent(Module module,
                                    ProjectRootManagerImpl projectRootManager,
                                    VirtualFilePointerManager filePointerManager) {
    super(module, projectRootManager, filePointerManager);
  }

  @NotNull
  @Override
  public Resolution getResolution(@NotNull Storage storage, @NotNull StateStorageOperation operation) {
    boolean isDefault = storage.storageClass() == StateStorage.class;
    boolean isEffectiveStorage = ClassPathStorageUtil.isDefaultStorage(getModule()) == isDefault;
    if (operation == StateStorageOperation.READ) {
      return isEffectiveStorage ? Resolution.DO : Resolution.SKIP;
    }
    else {
      // IDEA-133480 Eclipse integration: .iml content is not reduced on setting Dependencies Storage Format = Eclipse
      return isEffectiveStorage ? Resolution.DO : (isDefault ? Resolution.CLEAR : Resolution.SKIP);
    }
  }
}
