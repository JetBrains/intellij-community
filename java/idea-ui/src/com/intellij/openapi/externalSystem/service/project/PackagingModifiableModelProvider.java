// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.externalSystem.project.PackagingModifiableModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public final class PackagingModifiableModelProvider implements ModifiableModelsProviderExtension<PackagingModifiableModel> {

  @Override
  public @NotNull Pair<Class<PackagingModifiableModel>, PackagingModifiableModel> create(@NotNull Project project,
                                                                                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    return Pair.create(PackagingModifiableModel.class, new PackagingModifiableModelImpl(project, modelsProvider));
  }
}
