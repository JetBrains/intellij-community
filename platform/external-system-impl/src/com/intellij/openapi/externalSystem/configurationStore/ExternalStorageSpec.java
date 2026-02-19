// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.configurationStore;

import com.intellij.configurationStore.DirectoryBasedStorage;
import com.intellij.configurationStore.FileStorageAnnotation;
import com.intellij.configurationStore.StateStorageManager;
import com.intellij.configurationStore.StorageCreator;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ExternalStorageSpec extends FileStorageAnnotation implements StorageCreator {
  private final State inProjectStateSpec;

  public ExternalStorageSpec(@NotNull String path, @Nullable State inProjectStateSpec) {
    super(path, false);
    this.inProjectStateSpec = inProjectStateSpec;
  }

  @Override
  public @NotNull StateStorage create(@NotNull StateStorageManager storageManager) {
    ComponentManager componentManager = storageManager.getComponentManager();
    assert componentManager != null;
    if (path.equals(StoragePathMacros.MODULE_FILE)) {
      return new ExternalModuleStorage((Module)componentManager, storageManager);
    }

    var project = (Project)componentManager;
    if (inProjectStateSpec == null) {
      return new ExternalProjectStorage(path, project, storageManager, "project");
    }
    else {
      var storage = (DirectoryBasedStorage)storageManager.getStateStorage(inProjectStateSpec.storages()[0]);
      return new ExternalProjectFilteringStorage(path, project, storageManager, inProjectStateSpec.name(), storage);
    }
  }

  @Override
  public @NotNull String getKey() {
    return "external//" + path;
  }
}
