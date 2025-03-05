// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Vladislav.Soroka
 */
public abstract class AbstractProjectDataService<E, I> implements ProjectDataService<E, I> {

  public final Computable.PredefinedValueComputable<Collection<I>> EMPTY_LIST =
    new Computable.PredefinedValueComputable<>(Collections.emptyList());

  @Override
  public abstract @NotNull Key<E> getTargetDataKey();

  @Override
  public void importData(@NotNull Collection<? extends DataNode<E>> toImport,
                         @Nullable ProjectData projectData,
                         @NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
  }

  @Override
  public @NotNull Computable<Collection<I>> computeOrphanData(@NotNull Collection<? extends DataNode<E>> toImport,
                                                              @NotNull ProjectData projectData,
                                                              @NotNull Project project,
                                                              @NotNull IdeModifiableModelsProvider modelsProvider) {
    return EMPTY_LIST;
  }

  @Override
  public void removeData(Computable<? extends Collection<? extends I>> toRemoveComputable,
                         @NotNull Collection<? extends DataNode<E>> toIgnore,
                         @NotNull ProjectData projectData,
                         @NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
  }
}
