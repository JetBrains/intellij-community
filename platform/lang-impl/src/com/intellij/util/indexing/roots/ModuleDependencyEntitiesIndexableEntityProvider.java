// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.util.indexing.roots.kind.IndexableSetSelfDependentOrigin;
import com.intellij.util.indexing.roots.origin.LibrarySelfDependentOriginImpl;
import com.intellij.util.indexing.roots.origin.SdkSelfDependentOriginImpl;
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryEntityUtils;
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableRootModelBridge;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryId;
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleDependencyItem;
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ModuleDependencyEntitiesIndexableEntityProvider implements IndexableEntityProvider.ExistingEx<ModuleEntity> {

  @Override
  public @NotNull Class<ModuleEntity> getEntityClass() {
    return ModuleEntity.class;
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getExistingEntityIteratorBuilder(@NotNull ModuleEntity entity,
                                                                                                  @NotNull Project project) {
    List<IndexableIteratorBuilder> iterators = new SmartList<>();
    for (ModuleDependencyItem dependency : entity.getDependencies()) {
      iterators.addAll(createIteratorBuildersForDependency(dependency));
    }
    return iterators;
  }

  @Override
  public @NotNull Collection<? extends IndexableSetSelfDependentOrigin> getExistingEntityIteratorOrigins(@NotNull ModuleEntity entity,
                                                                                                         @NotNull EntityStorage storage,
                                                                                                         @NotNull Project project) {
    List<IndexableSetSelfDependentOrigin> origins = new SmartList<>();
    for (ModuleDependencyItem dependency : entity.getDependencies()) {
      origins.addAll(createIndexableSetOrigin(dependency, storage, project));
    }
    return origins;
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getIteratorBuildersForExistingModule(@NotNull ModuleEntity entity,
                                                                                                      @NotNull EntityStorage entityStorage,
                                                                                                      @NotNull Project project) {
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull ModuleEntity entity,
                                                                                                @NotNull Project project) {
    List<IndexableIteratorBuilder> iterators = new SmartList<>();
    iterators.addAll(IndexableIteratorBuilders.INSTANCE.forModuleContent(entity.getPersistentId()));
    for (ModuleDependencyItem dependency : entity.getDependencies()) {
      iterators.addAll(createIteratorBuildersForDependency(dependency));
    }
    return iterators;
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull ModuleEntity oldEntity,
                                                                                                   @NotNull ModuleEntity newEntity) {
    List<IndexableIteratorBuilder> iterators = new SmartList<>();
    List<ModuleDependencyItem> newDependencies = newEntity.getDependencies();
    Collection<ModuleDependencyItem> oldDependencies = new HashSet<>(oldEntity.getDependencies());
    for (ModuleDependencyItem dependency : newDependencies) {
      if (!oldDependencies.contains(dependency)) {
        iterators.addAll(createIteratorBuildersForDependency(dependency));
      }
    }
    if (!iterators.isEmpty()) {
      return iterators;
    }
    return Collections.emptyList();
  }

  @NotNull
  private static Collection<? extends IndexableIteratorBuilder> createIteratorBuildersForDependency(@NotNull ModuleDependencyItem dependency) {
    if (dependency instanceof ModuleDependencyItem.SdkDependency) {
      return IndexableIteratorBuilders.INSTANCE.forSdk(((ModuleDependencyItem.SdkDependency)dependency).getSdkName(),
                                                       ((ModuleDependencyItem.SdkDependency)dependency).getSdkType());
    }
    else if (dependency instanceof ModuleDependencyItem.Exportable.LibraryDependency) {
      LibraryId libraryId = ((ModuleDependencyItem.Exportable.LibraryDependency)dependency).getLibrary();
      return IndexableIteratorBuilders.INSTANCE.forLibraryEntity(libraryId, true);
    }
    else if (dependency instanceof ModuleDependencyItem.InheritedSdkDependency) {
      return IndexableIteratorBuilders.INSTANCE.forInheritedSdk();
    }
    return Collections.emptyList();
  }

  @NotNull
  private static Collection<? extends IndexableSetSelfDependentOrigin> createIndexableSetOrigin(@NotNull ModuleDependencyItem dependency,
                                                                                                @NotNull EntityStorage storage,
                                                                                                @NotNull Project project) {
    if (dependency instanceof ModuleDependencyItem.SdkDependency) {
      Sdk sdk = ModifiableRootModelBridge.findSdk(((ModuleDependencyItem.SdkDependency)dependency).getSdkName(),
                                                  ((ModuleDependencyItem.SdkDependency)dependency).getSdkType());
      if (sdk != null) {
        Collection<VirtualFile> rootsToIndex = SdkIndexableFilesIteratorImpl.Companion.getRootsToIndex(sdk);
        return Collections.singletonList(new SdkSelfDependentOriginImpl(sdk, rootsToIndex));
      }
    }
    else if (dependency instanceof ModuleDependencyItem.Exportable.LibraryDependency) {
      LibraryId libraryId = ((ModuleDependencyItem.Exportable.LibraryDependency)dependency).getLibrary();
      Library library = LibraryEntityUtils.findLibraryBridge(libraryId, storage, project);
      if (library != null) {
        List<VirtualFile> classFiles = LibraryIndexableFilesIteratorImpl.Companion.collectFiles(library, OrderRootType.CLASSES, null);
        List<VirtualFile> sourceFiles = LibraryIndexableFilesIteratorImpl.Companion.collectFiles(library, OrderRootType.SOURCES, null);
        return Collections.singletonList(
          new LibrarySelfDependentOriginImpl(classFiles, sourceFiles, Arrays.asList(((LibraryEx)library).getExcludedRoots())));
      }
    }
    else if (dependency instanceof ModuleDependencyItem.InheritedSdkDependency) {
      Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
      if (sdk != null) {
        Collection<VirtualFile> rootsToIndex = SdkIndexableFilesIteratorImpl.Companion.getRootsToIndex(sdk);
        return Collections.singletonList(new SdkSelfDependentOriginImpl(sdk, rootsToIndex));
      }
    }
    return Collections.emptyList();
  }
}
