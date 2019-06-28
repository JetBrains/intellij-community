// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface ModifiableModelsProviderExtension<T extends ModifiableModel> {
  @NotNull
  Pair<Class<T>, T> create(@NotNull Project project, @NotNull IdeModifiableModelsProvider modelsProvider);
}
