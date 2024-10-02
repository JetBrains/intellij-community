// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.workspace.storage.EntityPointer;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.util.indexing.customizingIteration.ExternalEntityIndexableIterator;
import com.intellij.util.indexing.customizingIteration.GenericContentEntityIterator;
import com.intellij.util.indexing.customizingIteration.ModuleAwareContentEntityIterator;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @deprecated Customization of indexing algorythm is available only above {@link WorkspaceFileIndexContributor} API:
 * roots may be recursive and non-recursive. This enforces consistency of project model and project indexes.
 * <p>
 * Presentation may be customized with {@link CustomizingIndexingPresentationContributor}
 */
@ApiStatus.Internal
@ApiStatus.OverrideOnly
@ApiStatus.Experimental
@Deprecated(forRemoval = true)
public interface CustomizingIndexingContributor<E extends WorkspaceEntity, D> extends WorkspaceFileIndexContributor<E> {

  @Nullable
  D getCustomizationData(@NotNull E entity);

  @NotNull
  Collection<? extends ModuleAwareContentEntityIterator> createModuleAwareContentIterators(@NotNull Module module,
                                                                                           @NotNull EntityPointer<E> reference,
                                                                                           @NotNull Collection<? extends VirtualFile> roots,
                                                                                           @Nullable D customization);
  @NotNull
  Collection<? extends GenericContentEntityIterator> createGenericContentIterators(@NotNull EntityPointer<E> reference,
                                                                                   @NotNull Collection<? extends VirtualFile> roots,
                                                                                   @Nullable D customization);

  Collection<? extends ExternalEntityIndexableIterator> createExternalEntityIterators(@NotNull EntityPointer<E> reference,
                                                                                      @NotNull Collection<? extends VirtualFile> roots,
                                                                                      @NotNull Collection<? extends VirtualFile> sourceRoots,
                                                                                      @Nullable D customization);
}
