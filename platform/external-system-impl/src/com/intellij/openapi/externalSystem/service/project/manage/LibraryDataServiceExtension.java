// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface LibraryDataServiceExtension {

  @Nullable
  PersistentLibraryKind<?> getLibraryKind(@NotNull LibraryData libraryData);

  void prepareNewLibrary(
    @NotNull IdeModifiableModelsProvider modelsProvider,
    @NotNull Library library,
    @NotNull LibraryData libraryData
  );
}
