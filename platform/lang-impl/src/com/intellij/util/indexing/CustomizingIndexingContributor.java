// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.customizingIteration.ExternalEntityIndexableIterator;
import com.intellij.util.indexing.customizingIteration.GenericContentEntityIterator;
import com.intellij.util.indexing.customizingIteration.ModuleAwareContentEntityIterator;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar;
import com.intellij.workspaceModel.storage.EntityReference;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * {@link CustomizingIndexingContributor} is designed to be able to customize iteration of files
 * corresponding to workspace entities of a certain class E during indexing.
 * <p><ul>
 * <li>
 * First based on workspace entities and their roots registered in
 * {@link CustomizingIndexingContributor#registerFileSets(WorkspaceEntity, WorkspaceFileSetRegistrar, EntityStorage)}
 * platform determines which roots should be iterated for an entity of class E. </li>
 * <li> Then for that entity additional data D is calculated with
 * {@link CustomizingIndexingContributor#getCustomizationData(WorkspaceEntity)}
 * </li>
 * <li>  Registered roots are grouped by {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind} they are registered with
 * <ul>
 *    <li>  Iterators for roots from {@code WorkspaceFileKind.EXTERNAL} and {@code WorkspaceFileKind.EXTERNAL_SOURCE} are created with
 *    {@link CustomizingIndexingContributor#createExternalEntityIterators(EntityReference, Collection, Collection, Object)}
 *    </li>
 *    <li>  Iterators for roots from {@code WorkspaceFileKind.CONTENT} and {@code WorkspaceFileKind.TEST_CONTENT} are created with
 *    {@link CustomizingIndexingContributor#createGenericContentIterators(EntityReference, Collection, Object)} or
 *    {@link CustomizingIndexingContributor#createModuleAwareContentIterators(Module, EntityReference, Collection, Object)}
 *    depending on their {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData} being subclass of
 *    {@link com.intellij.workspaceModel.core.fileIndex.impl.ModuleContentOrSourceRootData}.
 * <p>
 *    The difference between this two types of registrations is in {@link com.intellij.openapi.roots.ProjectFileIndex#getModuleForFile(VirtualFile)}
 *    and {@link com.intellij.openapi.roots.ProjectFileIndex#getContentRootForFile(VirtualFile)} behaviour.
 *    Many APIs in platform still expect all files in project content to belong to some module, so providing it may be safer.
 *    However in shining future platform moves to plugins won't have to support modules for such compatibility.
 *    </li>
 * </ul>
 * </li>
 * </ul>
 */
@ApiStatus.OverrideOnly
@ApiStatus.Experimental
public interface CustomizingIndexingContributor<E extends WorkspaceEntity, D> extends WorkspaceFileIndexContributor<E> {

  /**
   * @param entity entity whose roots should be indexed
   * @return data necessary to create iterators in createGenericContentIterators, createModuleAwareContentIterators,
   * createExternalEntityIterators. Collect it here to avoid resolving {@link EntityReference} later.
   */
  @Nullable
  D getCustomizationData(@NotNull E entity);

  /**
   * @param reference     reference of an entity which was provided in getCustomizationData.
   *                      Should not be resolved – data should be collected in getCustomizationData
   * @param roots         roots registered as {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind#CONTENT} or
   *                      {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind#TEST_CONTENT} for that entity,
   *                      with {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData} being subclass of
   *                      {@link com.intellij.workspaceModel.core.fileIndex.impl.ModuleContentOrSourceRootData},
   *                      and so the entity aware of a module. Currently, it's a recommended way to register content roots.
   * @param customization data precalculated in getCustomizationData
   * @return iterators that iterate roots, maybe filtering out some files.
   * @see ModuleAwareContentEntityIterator
   * @see com.intellij.util.indexing.roots.ModuleAwareContentEntityIteratorImpl
   */
  @NotNull
  Collection<? extends ModuleAwareContentEntityIterator> createModuleAwareContentIterators(@NotNull Module module,
                                                                                           @NotNull EntityReference<E> reference,
                                                                                           @NotNull Collection<? extends VirtualFile> roots,
                                                                                           @Nullable D customization);

  /**
   * @param reference     reference of an entity which was provided in getCustomizationData.
   *                      Should not be resolved – data should be collected in getCustomizationData
   * @param roots         roots registered as {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind#CONTENT} or
   *                      {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind#TEST_CONTENT} for that entity,
   *                      with {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData} not being subclass of
   *                      {@link com.intellij.workspaceModel.core.fileIndex.impl.ModuleContentOrSourceRootData},
   *                      and so the entity unaware of a module. This way may result in some unexpected complications
   *                      with other API expecting all content roots belong to some module. Or maybe not.
   * @param customization data precalculated in getCustomizationData
   * @return iterators that iterate roots, maybe filtering out some files.
   * @see GenericContentEntityIterator
   * @see com.intellij.util.indexing.roots.GenericContentEntityIteratorImpl
   */
  @NotNull
  Collection<? extends GenericContentEntityIterator> createGenericContentIterators(@NotNull EntityReference<E> reference,
                                                                                   @NotNull Collection<? extends VirtualFile> roots,
                                                                                   @Nullable D customization);

  /**
   * @param reference     reference of an entity which was provided in getCustomizationData.
   *                      Should not be resolved – data should be collected in getCustomizationData
   * @param roots         roots registered as {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind#EXTERNAL} for that entity
   * @param sourceRoots   roots registered as {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind#EXTERNAL_SOURCE} for that entity
   * @param customization data precalculated in getCustomizationData
   * @return iterators that iterate roots, maybe filtering out some files.
   * @see ExternalEntityIndexableIterator
   * @see com.intellij.util.indexing.roots.ExternalEntityIndexableIteratorImpl
   */
  Collection<? extends ExternalEntityIndexableIterator> createExternalEntityIterators(@NotNull EntityReference<E> reference,
                                                                                      @NotNull Collection<? extends VirtualFile> roots,
                                                                                      @NotNull Collection<? extends VirtualFile> sourceRoots,
                                                                                      @Nullable D customization);
}
