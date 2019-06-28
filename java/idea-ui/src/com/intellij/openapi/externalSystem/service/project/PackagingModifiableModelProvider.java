// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.externalSystem.project.PackagingModifiableModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public class PackagingModifiableModelProvider implements ModifiableModelsProviderExtension<PackagingModifiableModel> {

  @NotNull
  @Override
  public Pair<Class<PackagingModifiableModel>, PackagingModifiableModel> create(@NotNull Project project,
                                                                                @NotNull IdeModifiableModelsProvider modelsProvider) {
    return Pair.create(PackagingModifiableModel.class, new PackagingModifiableModelImpl(project, modelsProvider));
  }
}
