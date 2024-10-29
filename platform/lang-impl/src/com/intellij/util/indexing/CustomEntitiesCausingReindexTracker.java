// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.platform.workspace.jps.entities.*;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.roots.IndexableEntityProvider;
import com.intellij.workspaceModel.core.fileIndex.DependencyDescription;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class CustomEntitiesCausingReindexTracker {
  private @NotNull Set<Class<? extends WorkspaceEntity>> customEntitiesToRescan;

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
    WorkspaceFileIndexImpl.Companion.getEP_NAME().addExtensionPointListener(
      (ExtensionPointListener<WorkspaceFileIndexContributor<?>>)listener);

    customEntitiesToRescan = listCustomEntitiesCausingRescan();
  }


  private void reinit() {
    customEntitiesToRescan = listCustomEntitiesCausingRescan();
  }

  private static Set<Class<? extends WorkspaceEntity>> listCustomEntitiesCausingRescan() {
    Stream<Class<? extends WorkspaceEntity>> allClasses = WorkspaceFileIndexImpl.Companion.getEP_NAME().getExtensionList().stream()
      .flatMap(contributor -> getEntityClassesToCauseReindexing(contributor));
    allClasses = Stream.concat(allClasses,
                               IndexableEntityProvider.EP_NAME.getExtensionList().stream()
                                 .filter(provider -> provider instanceof IndexableEntityProvider.Enforced<? extends WorkspaceEntity>)
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

  /**
   * If we have a custom logic for entity on reindexing, it should be mentioned in {@link #isEntityReindexingCustomised} method
   */
  boolean shouldRescan(@Nullable WorkspaceEntity oldEntity, @Nullable WorkspaceEntity newEntity, @NotNull Project project) {
    if (oldEntity == null && newEntity == null) throw new RuntimeException("Either old or new entity should not be null");

    // ModuleEntity should throw rootChanged only on change of dependencies.
    if (newEntity instanceof ModuleEntity newModuleEntity && oldEntity instanceof ModuleEntity oldModuleEntity) {
      boolean haveSameDependencies = newModuleEntity.getDependencies().equals(oldModuleEntity.getDependencies());
      return !haveSameDependencies;
    }

    // Only url and exclude patterns affect indexing of `ContentRootEntity`. Changes of parent or children
    //   should not be considered as a reason for the rootsChanged event.
    if (newEntity instanceof ContentRootEntity newContentRootEntity && oldEntity instanceof ContentRootEntity oldContentRootEntity) {
      return !newContentRootEntity.getUrl().equals(oldContentRootEntity.getUrl()) ||
             !newContentRootEntity.getExcludedPatterns().equals(oldContentRootEntity.getExcludedPatterns());
    }

    // The rootsChanged is not thrown if the order of root groups has changed. Root group - group of roots collected by type.
    //   rootsChanged is still thrown if the order of roots inside one group changes.
    if (newEntity instanceof LibraryEntity newLibraryEntity && oldEntity instanceof LibraryEntity oldLibraryEntity) {
      if (newLibraryEntity.getTableId().equals(oldLibraryEntity.getTableId()) &&
          newLibraryEntity.getRoots().size() == oldLibraryEntity.getRoots().size() &&
          newLibraryEntity.getRoots().stream().collect(Collectors.groupingBy(o -> o.getType()))
            .equals(oldLibraryEntity.getRoots().stream().collect(Collectors.groupingBy(o -> o.getType())))) {
        return false;
      }
    }

    WorkspaceEntity entity = newEntity != null ? newEntity : oldEntity;

    // `rootsChanged` should not be thrown for changes in global libraries that are not presented in a project
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
