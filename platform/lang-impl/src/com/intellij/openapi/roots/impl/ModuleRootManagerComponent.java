/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.module.impl.ModuleEx;
import com.intellij.openapi.roots.impl.libraries.LibraryTableBase;
import com.intellij.openapi.roots.impl.storage.ClassPathStorageUtil;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
                                                                      PersistentStateComponentWithModificationTracker<ModuleRootManagerImpl.ModuleRootManagerState>,
                                                                      StateStorageChooserEx {
  public ModuleRootManagerComponent(Module module,
                                    ProjectRootManagerImpl projectRootManager,
                                    VirtualFilePointerManager filePointerManager) {
    super(module, projectRootManager, filePointerManager);
  }

  @NotNull
  @Override
  public Resolution getResolution(@NotNull Storage storage, @NotNull StateStorageOperation operation) {
    boolean isClasspathStorage = storage.storageClass() == ClasspathStorage.class;
    boolean isEffectiveStorage = ClassPathStorageUtil.isClasspathStorage(getModule()) == isClasspathStorage;
    if (operation == StateStorageOperation.READ) {
      return isEffectiveStorage ? Resolution.DO : Resolution.SKIP;
    }
    else {
      // IDEA-133480 Eclipse integration: .iml content is not reduced on setting Dependencies Storage Format = Eclipse
      // We clear any storage except eclipse (because we must not clear shared files).
      // Currently there is only one known non-default storage - ExternalProjectStorage.
      return isEffectiveStorage ? Resolution.DO : isClasspathStorage ? Resolution.SKIP : Resolution.CLEAR;
    }
  }

  @Override
  public long getStateModificationCount() {
    Module module = getModule();
    if (!module.isLoaded() || !(module instanceof ModuleEx)) {
      return myModificationCount;
    }

    final long[] result = {myModificationCount};
    result[0] += ((ModuleEx)module).getOptionsModificationCount();
    final List<String> handledLibraryTables = new SmartList<>();
    getRootModel().orderEntries().forEachLibrary(library -> {
      LibraryTable table = library.getTable();
      if (table instanceof LibraryTableBase && !handledLibraryTables.contains(table.getTableLevel())) {
        handledLibraryTables.add(table.getTableLevel());
        long count = ((LibraryTableBase)table).getStateModificationCount();
        if (count > 0) {
          if (Registry.is("store.track.module.root.manager.changes", false)) {
            LOG.error("modification count changed due to library  " + library.getName() + " change (" + count + "), module " + getModule().getName());
          }
        }
        result[0] += count;
      }
      return true;
    });
    return result[0] + myRootModel.getStateModificationCount();
  }
}
