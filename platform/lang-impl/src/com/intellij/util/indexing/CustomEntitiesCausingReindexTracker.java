// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.platform.workspace.jps.entities.ContentRootEntity;
import com.intellij.platform.workspace.jps.entities.DependenciesKt;
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity;
import com.intellij.platform.workspace.jps.entities.LibraryEntity;
import com.intellij.platform.workspace.jps.entities.LibraryPropertiesEntity;
import com.intellij.platform.workspace.jps.entities.ModuleEntity;
import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntity;
import com.intellij.platform.workspace.jps.entities.RootsKt;
import com.intellij.platform.workspace.jps.entities.SdkEntity;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.workspaceModel.core.fileIndex.DependencyDescription;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class CustomEntitiesCausingReindexTracker {
  private Set<Class<? extends WorkspaceEntity>> customEntitiesToRescan;

  CustomEntitiesCausingReindexTracker() {
    //noinspection deprecation
    WorkspaceFileIndexImpl.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override public void extensionAdded(WorkspaceFileIndexContributor<?> extension, @NotNull PluginDescriptor pluginDescriptor) { reinit(); }
      @Override public void extensionRemoved(WorkspaceFileIndexContributor<?> extension, @NotNull PluginDescriptor pluginDescriptor) { reinit(); }
    });

    customEntitiesToRescan = listCustomEntitiesCausingRescan();
  }

  private void reinit() {
    customEntitiesToRescan = listCustomEntitiesCausingRescan();
  }

  private static Set<Class<? extends WorkspaceEntity>> listCustomEntitiesCausingRescan() {
    return WorkspaceFileIndexImpl.EP_NAME.getExtensionList().stream()
      .flatMap(contributor -> getEntityClassesToCauseReindexing(contributor))
      .filter(aClass -> !isEntityReindexingCustomised(aClass))
      .collect(Collectors.toUnmodifiableSet());
  }

  private static Stream<Class<? extends WorkspaceEntity>> getEntityClassesToCauseReindexing(WorkspaceFileIndexContributor<?> contributor) {
    var dependencies = contributor.getDependenciesOnOtherEntities();
    var baseStream = Stream.<Class<? extends WorkspaceEntity>>of(contributor.getEntityClass());
    if (dependencies.isEmpty()) return baseStream;
    var parentDependenciesStream = dependencies.stream()
      .filter(description -> description instanceof DependencyDescription.OnParent)
      .map(description -> ((DependencyDescription.OnParent<?, ?>)description).getParentClass());
    var onEntityDeps = dependencies.stream()
      .filter(description -> description instanceof DependencyDescription.OnArbitraryEntity)
      .map(description -> ((DependencyDescription.OnArbitraryEntity<?, ?>)description).getEntityClass());
    var baseAndParentsDependenciesStream = Stream.concat(baseStream, parentDependenciesStream);
    return Stream.concat(baseAndParentsDependenciesStream, onEntityDeps);
  }

  private static boolean isEntityReindexingCustomised(Class<? extends WorkspaceEntity> entityClass) {
    return (
      LibraryEntity.class.isAssignableFrom(entityClass) ||
      LibraryPropertiesEntity.class.isAssignableFrom(entityClass) ||
      SdkEntity.class.isAssignableFrom(entityClass)
    );
  }

  /**
   * If we have a custom logic for entity on reindexing, it should be mentioned in {@link #isEntityReindexingCustomised} method
   */
  boolean shouldRescan(@Nullable WorkspaceEntity oldEntity, @Nullable WorkspaceEntity newEntity, @NotNull Project project) {
    if (oldEntity == null && newEntity == null) throw new RuntimeException("Either old or new entity should not be null");

    // `ModuleEntity` should throw "rootChanged" only on change of dependencies.
    if (newEntity instanceof ModuleEntity newModuleEntity && oldEntity instanceof ModuleEntity oldModuleEntity) {
      var haveSameDependencies = newModuleEntity.getDependencies().equals(oldModuleEntity.getDependencies());
      return !haveSameDependencies;
    }

    // Only URL and exclude patterns affect indexing of `ContentRootEntity`.
    // Changes of parents or children should not be considered as a reason for the "rootsChanged" event.
    if (newEntity instanceof ContentRootEntity newCRE && oldEntity instanceof ContentRootEntity oldCRE) {
      return !newCRE.getUrl().equals(oldCRE.getUrl()) || !newCRE.getExcludedPatterns().equals(oldCRE.getExcludedPatterns());
    }

    // The "rootsChanged" is not thrown if the order of root groups has changed. Root group - group of roots collected by type.
    // The "rootsChanged" is still thrown if the order of roots inside one group changes.
    if (
      newEntity instanceof LibraryEntity newLE &&
      oldEntity instanceof LibraryEntity oldLE &&
      newLE.getTableId().equals(oldLE.getTableId()) &&
      newLE.getRoots().size() == oldLE.getRoots().size() &&
      Objects.equals(
        newLE.getRoots().stream().collect(Collectors.groupingBy(o -> o.getType())),
        oldLE.getRoots().stream().collect(Collectors.groupingBy(o -> o.getType()))
      )
    ) {
      return false;
    }

    var entity = newEntity != null ? newEntity : oldEntity;

    // "rootsChanged" should not be thrown for changes in global libraries that are not presented in a project
    if (entity instanceof LibraryEntity libEntity) {
      return hasDependencyOn(libEntity, project);
    }
    else if (entity instanceof LibraryPropertiesEntity libPropEntity) {
      return hasDependencyOn(libPropEntity.getLibrary(), project);
    }
    else if (entity instanceof ExcludeUrlEntity exUrlEntity) {
      var library = DependenciesKt.getLibrary(exUrlEntity);
      if (library != null) {
        return hasDependencyOn(library, project);
      }
      var contentRoot = RootsKt.getContentRoot(exUrlEntity);
      if (contentRoot != null) {
        return isEntityToRescan(contentRoot);
      }
      return false;
    }
    else if (entity instanceof SdkEntity sdkEntity) {
      return hasDependencyOn(sdkEntity, project);
    }
    else if (entity instanceof ProjectSettingsEntity) {
      return true; // don't care if there are references from modules or not: project sdk is always indexed
    }
    return isEntityToRescan(entity);
  }

  private boolean isEntityToRescan(@NotNull WorkspaceEntity entity) {
    var entityClass = entity.getClass();
    return ContainerUtil.exists(customEntitiesToRescan, aClass -> aClass.isAssignableFrom(entityClass));
  }

  private static boolean hasDependencyOn(LibraryEntity library, Project project) {
    return ModuleDependencyIndex.getInstance(project).hasDependencyOn(library.getSymbolicId());
  }

  private static boolean hasDependencyOn(SdkEntity sdkEntity, Project project) {
    return ModuleDependencyIndex.getInstance(project).hasDependencyOn(sdkEntity.getSymbolicId());
  }
}
