// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.customizingIteration.ExternalEntityIndexableIterator;
import com.intellij.util.indexing.customizingIteration.ModuleAwareContentEntityIterator;
import com.intellij.util.indexing.customizingIteration.ModuleUnawareContentEntityIterator;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor;
import com.intellij.workspaceModel.storage.EntityReference;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface CustomizingIndexingContributor<E extends WorkspaceEntity, D> extends WorkspaceFileIndexContributor<E> {

  @Nullable
  D getCustomizationData(@NotNull E entity);

  @NotNull
  Collection<? extends ModuleAwareContentEntityIterator> createModuleAwareContentIterators(@NotNull Module module,
                                                                                           @NotNull EntityReference<E> reference,
                                                                                           @NotNull Collection<? extends VirtualFile> roots,
                                                                                           @Nullable D customization);

  @NotNull
  Collection<? extends ModuleUnawareContentEntityIterator> createModuleUnawareContentIterators(@NotNull EntityReference<E> reference,
                                                                                               @NotNull Collection<? extends VirtualFile> roots,
                                                                                               @Nullable D customization);

  Collection<? extends ExternalEntityIndexableIterator> createExternalEntityIterators(@NotNull EntityReference<E> reference,
                                                                                      @NotNull Collection<? extends VirtualFile> roots,
                                                                                      @NotNull Collection<? extends VirtualFile> sourceRoots,
                                                                                      @Nullable D customization);
}
