// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.util.indexing.roots.kind.IndexableSetSelfDependentOrigin;
import com.intellij.util.indexing.roots.origin.ModuleRootSelfDependentOriginImpl;
import com.intellij.workspaceModel.ide.VirtualFileUrlManagerUtil;
import com.intellij.workspaceModel.ide.impl.UtilsKt;
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleEntityUtils;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.ExtensionsKt;
import com.intellij.workspaceModel.storage.bridgeEntities.api.ContentRootEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.api.SourceRootEntity;
import com.intellij.workspaceModel.storage.url.VirtualFileUrl;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

class SourceRootIndexableEntityProvider implements IndexableEntityProvider.ParentEntityDependent<SourceRootEntity, ContentRootEntity>,
                                                   IndexableEntityProvider.ExistingEx<SourceRootEntity> {

  @Override
  public @NotNull Class<SourceRootEntity> getEntityClass() {
    return SourceRootEntity.class;
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getIteratorBuildersForExistingModule(@NotNull ModuleEntity entity,
                                                                                                      @NotNull EntityStorage entityStorage,
                                                                                                      @NotNull Project project) {
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(entity.getPersistentId(),
                                                             collectRootUrls(ExtensionsKt.getSourceRoots(entity)));
  }

  @Override
  public @NotNull Collection<? extends IndexableSetSelfDependentOrigin> getExistingEntityIteratorOrigins(@NotNull SourceRootEntity entity,
                                                                                                         @NotNull EntityStorage storage,
                                                                                                         @NotNull Project project) {
    ModuleEntity moduleEntity = entity.getContentRoot().getModule();
    ModuleBridge module = ModuleEntityUtils.findModule(moduleEntity, storage);
    if (module == null) {
      return Collections.emptyList();
    }
    List<VirtualFileUrl> excludedUrls = entity.getContentRoot().getExcludedUrls();
    VirtualFileUrl rootUrl = entity.getUrl();
    boolean isExcluded = false;
    List<VirtualFileUrl> excludedSourceUrlsFiles = new SmartList<>();
    for (VirtualFileUrl excludedUrl : excludedUrls) {
      if (VirtualFileUrlManagerUtil.isEqualOrParentOf(excludedUrl, rootUrl)) {
        if (VirtualFileUrlManagerUtil.isEqualOrParentOf(rootUrl, excludedUrl)) {
          return Collections.emptyList();
        }
        isExcluded = true;
      }
      else if (VirtualFileUrlManagerUtil.isEqualOrParentOf(excludedUrl, rootUrl)) {
        excludedSourceUrlsFiles.add(excludedUrl);
      }
    }
    if (isExcluded) {
      return Collections.singletonList(
        new ModuleRootSelfDependentOriginImpl(module, Collections.singletonList(UtilsKt.toVirtualFile(entity.getUrl())),
                                              ContainerUtil.map(excludedSourceUrlsFiles, url -> UtilsKt.toVirtualFile(url))));
    }
    else {
      return Collections.emptyList();
    }
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull SourceRootEntity entity,
                                                                                                @NotNull Project project) {
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(entity.getContentRoot().getModule().getPersistentId(), entity.getUrl());
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull SourceRootEntity oldEntity,
                                                                                                   @NotNull SourceRootEntity newEntity) {
    if (!(newEntity.getUrl().equals(oldEntity.getUrl())) || !newEntity.getRootType().equals(oldEntity.getRootType())) {
      return IndexableIteratorBuilders.INSTANCE.forModuleRoots(newEntity.getContentRoot().getModule().getPersistentId(),
                                                               newEntity.getUrl());
    }
    return Collections.emptyList();
  }

  @NotNull
  private static List<VirtualFileUrl> collectRootUrls(List<SourceRootEntity> newContentRoots) {
    return ContainerUtil.map(newContentRoots, o -> o.getUrl());
  }

  @Override
  public @NotNull Class<ContentRootEntity> getParentEntityClass() {
    return ContentRootEntity.class;
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedParentEntityIteratorBuilder(@NotNull ContentRootEntity oldEntity,
                                                                                                        @NotNull ContentRootEntity newEntity,
                                                                                                        @NotNull Project project) {
    List<VirtualFileUrl> newRoots = collectRootUrls(newEntity.getSourceRoots());
    List<VirtualFileUrl> oldRoots = collectRootUrls(oldEntity.getSourceRoots());
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(newEntity.getModule().getPersistentId(), newRoots, oldRoots);
  }
}
