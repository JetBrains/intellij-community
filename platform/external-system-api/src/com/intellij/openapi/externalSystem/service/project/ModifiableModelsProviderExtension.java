// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Provides custom modifiable model for modification custom model that isn't stored or indirectly stored in workspace model.
 *
 * @param <T> modifiable model that will be provided into {@link IdeModifiableModelsProvider}.
 * @see IdeModifiableModelsProvider
 * @see ModifiableModel
 */
@ApiStatus.Experimental
public interface ModifiableModelsProviderExtension<T extends ModifiableModel> {

  /**
   * @param project        is project services container.
   * @param modelsProvider is intermediate provider that gives access for all custom modifiable models.
   * @return custom modifiable model and its class. It is needed to access this model from {@code modelsProvider}.
   */
  @NotNull Pair<Class<T>, T> create(@NotNull Project project, @NotNull IdeModifiableModelsProvider modelsProvider);
}
