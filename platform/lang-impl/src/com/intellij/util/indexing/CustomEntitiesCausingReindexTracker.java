// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.CustomEntityProjectModelInfoProvider;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.roots.IndexableEntityProvider;
import com.intellij.workspaceModel.core.fileIndex.DependencyDescription;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor;
import com.intellij.workspaceModel.core.fileIndex.impl.PlatformInternalWorkspaceFileIndexContributor;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class CustomEntitiesCausingReindexTracker {
  private final boolean useWorkspaceFileIndexContributors = IndexableFilesIndex.isEnabled();
  @NotNull
  private Set<Class<? extends WorkspaceEntity>> customEntitiesToRescan;

  CustomEntitiesCausingReindexTracker() {
    ExtensionPointListener<?> listener =
      new ExtensionPointListener<>() {
        @Override
        public void extensionAdded(Object extension,
                                   @NotNull PluginDescriptor pluginDescriptor) {
          reinit();
        }

        @Override
        public void extensionRemoved(Object extension,
                                     @NotNull PluginDescriptor pluginDescriptor) {
          reinit();
        }
      };
    //noinspection unchecked
    IndexableEntityProvider.EP_NAME.addExtensionPointListener(
      (ExtensionPointListener<IndexableEntityProvider<? extends WorkspaceEntity>>)listener);
    //noinspection unchecked
    CustomEntityProjectModelInfoProvider.EP.addExtensionPointListener(
      (ExtensionPointListener<CustomEntityProjectModelInfoProvider<?>>)listener);
    //noinspection unchecked
    WorkspaceFileIndexImpl.Companion.getEP_NAME().addExtensionPointListener(
      (ExtensionPointListener<WorkspaceFileIndexContributor<?>>)listener);

    customEntitiesToRescan = listCustomEntitiesCausingRescan();
  }


  private void reinit() {
    customEntitiesToRescan = listCustomEntitiesCausingRescan();
  }

  private Set<Class<? extends WorkspaceEntity>> listCustomEntitiesCausingRescan() {
    Stream<Class<? extends WorkspaceEntity>> allClasses =
      CustomEntityProjectModelInfoProvider.EP.getExtensionList().stream().map(provider -> provider.getEntityClass());
    allClasses = Stream.concat(allClasses,
                               WorkspaceFileIndexImpl.Companion.getEP_NAME().getExtensionList().stream()
                                 .filter(contributor -> useWorkspaceFileIndexContributors ||
                                                        !(contributor instanceof PlatformInternalWorkspaceFileIndexContributor))
                                 .flatMap(contributor -> getEntityClassesToCauseReindexing(contributor)));
    allClasses = Stream.concat(allClasses,
                               IndexableEntityProvider.EP_NAME.getExtensionList().stream()
                                 .filter(provider -> !useWorkspaceFileIndexContributors ||
                                                     provider instanceof IndexableEntityProvider.Enforced<? extends WorkspaceEntity>)
                                 .map(provider -> provider.getEntityClass()));
    return Set.copyOf(allClasses.filter(aClass -> !isEntityReindexingCustomised(aClass)).collect(Collectors.toSet()));
  }

  private static Stream<Class<? extends WorkspaceEntity>> getEntityClassesToCauseReindexing(WorkspaceFileIndexContributor<?> contributor) {
    List<? extends DependencyDescription<?>> dependencies = contributor.getDependenciesOnOtherEntities();
    Stream<Class<? extends WorkspaceEntity>> baseStream = Stream.of(contributor.getEntityClass());
    if (dependencies.isEmpty()) return baseStream;
    Stream<? extends Class<? extends WorkspaceEntity>> dependenciesStream =
      dependencies.stream().filter(description -> description instanceof DependencyDescription.OnParent)
        .map(description -> ((DependencyDescription.OnParent<?, ?>)description).getParentClass());
    return Stream.concat(baseStream, dependenciesStream);
  }

  private static boolean isEntityReindexingCustomised(Class<? extends WorkspaceEntity> entityClass) {
    return LibraryEntity.class.isAssignableFrom(entityClass) ||
           LibraryPropertiesEntity.class.isAssignableFrom(entityClass);
  }

  boolean shouldRescan(@NotNull WorkspaceEntity entity, @NotNull Project project) {
    if (entity instanceof LibraryEntity) {
      return hasDependencyOn((LibraryEntity)entity, project);
    }
    else if (entity instanceof LibraryPropertiesEntity) {
      return hasDependencyOn(((LibraryPropertiesEntity)entity).getLibrary(), project);
    }
    else if (entity instanceof ExcludeUrlEntity) {
      LibraryEntity library = DependenciesKt.getLibrary((ExcludeUrlEntity)entity);
      if (library != null) {
        return hasDependencyOn(library, project);
      }
      ContentRootEntity contentRoot = RootsKt.getContentRoot((ExcludeUrlEntity)entity);
      if (contentRoot != null) {
        return isEntityToRescan(contentRoot);
      }
      return false;
    }
    return isEntityToRescan(entity);
  }

  private boolean isEntityToRescan(@NotNull WorkspaceEntity entity) {
    Class<? extends @NotNull WorkspaceEntity> entityClass = entity.getClass();
    return ContainerUtil.exists(customEntitiesToRescan, aClass -> aClass.isAssignableFrom(entityClass));
  }

  private static boolean hasDependencyOn(LibraryEntity library, Project project) {
    return ModuleDependencyIndex.getInstance(project).hasDependencyOn(library.getSymbolicId());
  }
}
