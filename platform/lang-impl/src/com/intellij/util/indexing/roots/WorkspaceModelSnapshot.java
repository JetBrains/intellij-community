// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.roots.kind.IndexableSetSelfDependentOrigin;
import com.intellij.util.indexing.roots.origin.LibrarySelfDependentOriginImpl;
import com.intellij.util.indexing.roots.origin.SdkSelfDependentOriginImpl;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryEntityUtils;
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableRootModelBridge;
import com.intellij.workspaceModel.storage.*;
import com.intellij.workspaceModel.storage.bridgeEntities.api.*;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class WorkspaceModelSnapshot {
  private final ImmutableMap<EntityReference<? extends WorkspaceEntity>, IndexableSetSelfDependentOrigin> entitiesToOrigins;
  private final LibrariesSnapshot libraries;
  private final SdkSnapshot sdks;

  static WorkspaceModelSnapshot create(@NotNull Project project) {
    EntityStorage entityStorage = WorkspaceModel.getInstance(project).getEntityStorage().getCurrent();
    ModifiableLibrariesSnapshot snapshot = new ModifiableLibrariesSnapshot(MultiMap.createSet(), new HashMap<>());
    Set<SdkId> sdkIds = new HashSet<>();
    for (ModuleEntity entity : SequencesKt.asIterable(entityStorage.entities(ModuleEntity.class))) {
      for (ModuleDependencyItem dependency : entity.getDependencies()) {
        snapshot.addDependency(entity, dependency, entityStorage, project);
        if (dependency instanceof ModuleDependencyItem.SdkDependency) {
          sdkIds.add(SdkId.create((ModuleDependencyItem.SdkDependency)dependency));
        }
      }
    }
    return new WorkspaceModelSnapshot(createBuilder(project, entityStorage),
                                      snapshot.toImmutableSnapshot(),
                                      SdkSnapshot.create(sdkIds));
  }

  private WorkspaceModelSnapshot(@NotNull ImmutableMap.Builder<EntityReference<? extends WorkspaceEntity>, IndexableSetSelfDependentOrigin> builder,
                                 @NotNull LibrariesSnapshot librariesSnapshot,
                                 @NotNull SdkSnapshot sdkSnapshot) {
    this(builder.build(), librariesSnapshot, sdkSnapshot);
  }

  private WorkspaceModelSnapshot(@NotNull ImmutableMap<EntityReference<? extends WorkspaceEntity>, IndexableSetSelfDependentOrigin> entitiesToOrigins,
                                 @NotNull LibrariesSnapshot librariesSnapshot,
                                 @NotNull SdkSnapshot sdkSnapshot) {
    this.entitiesToOrigins = entitiesToOrigins;
    libraries = librariesSnapshot;
    sdks = sdkSnapshot;
  }

  private record SdkId(String name, String type) {
    @NotNull
    static SdkId create(@NotNull ModuleDependencyItem.SdkDependency dependency) {
      return new SdkId(dependency.getSdkName(), dependency.getSdkType());
    }
  }

  @NotNull
  private static ImmutableMap.Builder<EntityReference<? extends WorkspaceEntity>, IndexableSetSelfDependentOrigin> createBuilder(@NotNull Project project,
                                                                                                                                 @NotNull EntityStorage storage) {
    ImmutableMap.Builder<EntityReference<? extends WorkspaceEntity>, IndexableSetSelfDependentOrigin> builder =
      new ImmutableMap.Builder<>();
    for (IndexableEntityProvider<? extends WorkspaceEntity> provider : IndexableEntityProvider.EP_NAME.getExtensionList()) {
      if (!(provider instanceof IndexableEntityProvider.ExistingEx<?>)) {
        continue;
      }
      handleProvider((IndexableEntityProvider.ExistingEx<?>)provider, storage, project, builder);
    }
    return builder;
  }

  @NotNull
  Collection<? extends IndexableSetSelfDependentOrigin> getOrigins() {
    List<IndexableSetSelfDependentOrigin> result = new ArrayList<>(libraries.getOrigins());
    result.addAll(sdks.getOrigins());
    result.addAll(entitiesToOrigins.values());
    return result;
  }

  private static <E extends WorkspaceEntity> void handleProvider(@NotNull IndexableEntityProvider.ExistingEx<E> provider,
                                                                 @NotNull EntityStorage storage,
                                                                 @NotNull Project project,
                                                                 @NotNull ImmutableMap.Builder<EntityReference<? extends WorkspaceEntity>, IndexableSetSelfDependentOrigin> entities) {
    Class<E> aClass = provider.getEntityClass();
    for (E entity : SequencesKt.asIterable(storage.entities(aClass))) {
      addOrigins(entities, entity, provider, storage, project);
    }
  }

  @Nullable
  WorkspaceModelSnapshot createChangedIfNeeded(@NotNull VersionedStorageChange storageChange,
                                               @NotNull Project project) {
    EntityStorageSnapshot storage = storageChange.getStorageAfter();
    Map<EntityReference<? extends WorkspaceEntity>, IndexableSetSelfDependentOrigin> toAdd = new HashMap<>();
    Set<EntityReference<? extends WorkspaceEntity>> toRemove = new HashSet<>();
    for (IndexableEntityProvider<? extends WorkspaceEntity> provider : IndexableEntityProvider.EP_NAME.getExtensionList()) {
      if (provider instanceof IndexableEntityProvider.ExistingEx<?>) {
        handleWorkspaceModelChange(toAdd, toRemove, storageChange, (IndexableEntityProvider.ExistingEx<?>)provider, storage, project);
      }
    }
    LibrariesSnapshot changedLibraries = libraries.createChangedIfNeeded(storageChange, storage, project);
    if (toAdd.isEmpty() && toRemove.isEmpty() && changedLibraries == null) {
      return null;
    }
    ImmutableMap.Builder<EntityReference<? extends WorkspaceEntity>, IndexableSetSelfDependentOrigin> copy = new ImmutableMap.Builder<>();
    for (Map.Entry<EntityReference<? extends WorkspaceEntity>, IndexableSetSelfDependentOrigin> entry : entitiesToOrigins.entrySet()) {
      EntityReference<? extends WorkspaceEntity> entityReference = entry.getKey();
      boolean removed = toRemove.remove(entityReference);
      if (!removed) {
        IndexableSetSelfDependentOrigin add = toAdd.remove(entityReference);
        if (add == null) {
          copy.put(entityReference, entry.getValue());
        }
        else {
          copy.put(entityReference, add);
        }
      }
    }
    copy.putAll(toAdd);
    return new WorkspaceModelSnapshot(copy, changedLibraries == null ? libraries : changedLibraries, sdks);
  }

  private static <E extends WorkspaceEntity> void handleWorkspaceModelChange(@NotNull Map<EntityReference<? extends WorkspaceEntity>, IndexableSetSelfDependentOrigin> toAdd,
                                                                             @NotNull Set<EntityReference<? extends WorkspaceEntity>> toRemove,
                                                                             @NotNull VersionedStorageChange storageChange,
                                                                             @NotNull IndexableEntityProvider.ExistingEx<E> provider,
                                                                             @NotNull EntityStorageSnapshot storage,
                                                                             @NotNull Project project) {
    List<EntityChange<E>> changes = storageChange.getChanges(provider.getEntityClass());
    for (EntityChange<E> change : changes) {
      if (change instanceof EntityChange.Added<E>) {
        E entity = ((EntityChange.Added<E>)change).getEntity();
        addOrigins(toAdd, entity, provider, storage, project);
      }
      else if (change instanceof EntityChange.Replaced<E>) {
        E oldEntity = Objects.requireNonNull(change.getOldEntity());
        toRemove.add(oldEntity.createReference());

        E newEntity = ((EntityChange.Replaced<E>)change).getNewEntity();
        addOrigins(toAdd, newEntity, provider, storage, project);
      }
      else if (change instanceof EntityChange.Removed<E>) {
        E entity = ((EntityChange.Removed<E>)change).getEntity();
        toRemove.add(entity.createReference());
      }
      else {
        throw new IllegalStateException("Unexpected change " + change.getClass());
      }
    }
    if (provider instanceof ContentRootIndexableEntityProvider) {
      SourceRootIndexableEntityProvider sourceRootProvider =
        IndexableEntityProvider.EP_NAME.findExtensionOrFail(SourceRootIndexableEntityProvider.class);
      for (EntityChange<E> change : changes) {
        if (change instanceof EntityChange.Replaced<E>) {
          ContentRootEntity oldEntity = Objects.requireNonNull(((EntityChange.Replaced<ContentRootEntity>)change).getOldEntity());
          for (SourceRootEntity root : oldEntity.getSourceRoots()) {
            toRemove.add(root.createReference());
          }

          ContentRootEntity newEntity = ((EntityChange.Replaced<ContentRootEntity>)change).getNewEntity();
          for (SourceRootEntity sourceRootEntity : newEntity.getSourceRoots()) {
            addOrigins(toAdd, sourceRootEntity, sourceRootProvider, storage, project);
          }
        }
      }
    }
  }

  private static <E extends WorkspaceEntity> void addOrigins(@NotNull ImmutableMap.Builder<EntityReference<? extends WorkspaceEntity>, IndexableSetSelfDependentOrigin> entities,
                                                             @NotNull E entity,
                                                             @NotNull IndexableEntityProvider.ExistingEx<E> provider,
                                                             @NotNull EntityStorage storage,
                                                             @NotNull Project project) {
    IndexableSetSelfDependentOrigin origin = provider.getExistingEntityIteratorOrigins(entity, storage, project);
    if (origin != null) {
      entities.put(entity.createReference(), origin);
    }
  }

  private static <E extends WorkspaceEntity> void addOrigins(@NotNull Map<EntityReference<? extends WorkspaceEntity>, IndexableSetSelfDependentOrigin> entities,
                                                             @NotNull E entity,
                                                             @NotNull IndexableEntityProvider.ExistingEx<E> provider,
                                                             @NotNull EntityStorage storage,
                                                             @NotNull Project project) {
    IndexableSetSelfDependentOrigin origin = provider.getExistingEntityIteratorOrigins(entity, storage, project);
    if (origin != null) {
      entities.put(entity.createReference(), origin);
    }
  }

  @Nullable
  WorkspaceModelSnapshot createWithRefreshedEntitiesIfNeeded(@NotNull List<? extends WorkspaceEntity> entities,
                                                             @NotNull Project project) {
    if (entities.isEmpty()) return null;
    EntityStorage storage = WorkspaceModel.getInstance(project).getEntityStorage().getCurrent();
    Map<EntityReference<? extends WorkspaceEntity>, IndexableSetSelfDependentOrigin> refreshed = new HashMap<>();
    for (IndexableEntityProvider<? extends WorkspaceEntity> provider : IndexableEntityProvider.EP_NAME.getExtensionList()) {
      if (provider instanceof IndexableEntityProvider.ExistingEx<?>) {
        handleEntitiesRefresh((IndexableEntityProvider.ExistingEx<?>)provider, entities, project, storage, refreshed);
      }
    }
    if (refreshed.isEmpty()) return null;
    ImmutableMap.Builder<EntityReference<? extends WorkspaceEntity>, IndexableSetSelfDependentOrigin> result = new ImmutableMap.Builder<>();
    for (Map.Entry<EntityReference<? extends WorkspaceEntity>, IndexableSetSelfDependentOrigin> entry : entitiesToOrigins.entrySet()) {
      IndexableSetSelfDependentOrigin newValue = refreshed.get(entry.getKey());
      if (newValue != null) {
        result.put(entry.getKey(), newValue);
      }
      else {
        result.put(entry.getKey(), entry.getValue());
      }
    }
    return new WorkspaceModelSnapshot(result, libraries, sdks);
  }

  private static <E extends WorkspaceEntity> void handleEntitiesRefresh(@NotNull IndexableEntityProvider.ExistingEx<E> provider,
                                                                        @NotNull List<? extends WorkspaceEntity> entities,
                                                                        @NotNull Project project,
                                                                        @NotNull EntityStorage storage,
                                                                        @NotNull Map<EntityReference<? extends WorkspaceEntity>, IndexableSetSelfDependentOrigin> entitiesMap) {
    Class<E> aClass = provider.getEntityClass();
    for (WorkspaceEntity entity : entities) {
      if (aClass.isInstance(entity)) {
        //noinspection unchecked
        IndexableSetSelfDependentOrigin origin = provider.getExistingEntityIteratorOrigins((E)entity, storage, project);
        if (origin == null) {
          entities.remove(entity);
        }
        else {
          entitiesMap.put(entity.createReference(), origin);
        }
      }
    }
  }

  @Nullable
  WorkspaceModelSnapshot referencedLibraryAdded(@NotNull Library library) {
    ModifiableLibrariesSnapshot snapshot = ModifiableLibrariesSnapshot.create(libraries);
    snapshot.addLibrary(library);
    return new WorkspaceModelSnapshot(entitiesToOrigins, snapshot.toImmutableSnapshot(), sdks);
  }

  @Nullable
  WorkspaceModelSnapshot referencedLibraryChanged(@NotNull Library library) {
    return referencedLibraryAdded(library);
  }

  @Nullable
  WorkspaceModelSnapshot referencedLibraryRemoved(@NotNull Library library) {
    ModifiableLibrariesSnapshot snapshot = ModifiableLibrariesSnapshot.create(libraries);
    snapshot.removeLibrary(LibraryEntityUtils.findLibraryId(library));
    return new WorkspaceModelSnapshot(entitiesToOrigins, snapshot.toImmutableSnapshot(), sdks);
  }

  @Nullable
  WorkspaceModelSnapshot addedDependencyOn(@NotNull Sdk sdk) {
    ModifiableSdkSnapshot snapshot = ModifiableSdkSnapshot.create(sdks);
    snapshot.addSdk(sdk);
    return new WorkspaceModelSnapshot(entitiesToOrigins, libraries, snapshot.toImmutableSnapshot());
  }

  @Nullable
  WorkspaceModelSnapshot removedDependencyOn(@NotNull Sdk sdk) {
    ModifiableSdkSnapshot snapshot = ModifiableSdkSnapshot.create(sdks);
    snapshot.removeSdk(sdk);
    return new WorkspaceModelSnapshot(entitiesToOrigins, libraries, snapshot.toImmutableSnapshot());
  }

  @Nullable
  WorkspaceModelSnapshot referencedSdkAdded(@NotNull Sdk sdk) {
    return addedDependencyOn(sdk);
  }

  @Nullable
  WorkspaceModelSnapshot referencedSdkChanged(@NotNull Sdk sdk) {
    ModifiableSdkSnapshot snapshot = ModifiableSdkSnapshot.create(sdks);
    snapshot.updateSdk(sdk);
    return new WorkspaceModelSnapshot(entitiesToOrigins, libraries, snapshot.toImmutableSnapshot());
  }

  @Nullable
  WorkspaceModelSnapshot referencedSdkRemoved(@NotNull Sdk sdk) {
    return removedDependencyOn(sdk);
  }

  private static class LibrariesSnapshot {//todo[lene] write test for library rename
    private final ImmutableMap<LibraryId, Collection<ModuleEntity>> dependencies;
    private final ImmutableMap<LibraryId, IndexableSetSelfDependentOrigin> origins;


    private LibrariesSnapshot(ImmutableMap<LibraryId, Collection<ModuleEntity>> dependencies,
                              ImmutableMap<LibraryId, IndexableSetSelfDependentOrigin> origins) {
      this.dependencies = dependencies;
      this.origins = origins;
    }

    @NotNull
    private Collection<? extends IndexableSetSelfDependentOrigin> getOrigins() {
      return origins.values();
    }

    private LibrariesSnapshot createChangedIfNeeded(@NotNull VersionedStorageChange storageChange,
                                                    EntityStorageSnapshot storage,
                                                    Project project) {
      List<EntityChange<ModuleEntity>> moduleChanges = storageChange.getChanges(ModuleEntity.class);
      List<EntityChange<LibraryEntity>> libraryChanges = storageChange.getChanges(LibraryEntity.class);

      if (moduleChanges.isEmpty() && libraryChanges.isEmpty()) return null;

      ModifiableLibrariesSnapshot snapshot = ModifiableLibrariesSnapshot.create(this);

      for (EntityChange<ModuleEntity> change : moduleChanges) {
        if (change instanceof EntityChange.Added<ModuleEntity>) {
          snapshot.addDependencies(((EntityChange.Added<ModuleEntity>)change).getEntity(), storage, project);
        }
        else if (change instanceof EntityChange.Replaced<ModuleEntity>) {
          snapshot.changeDependencies(((EntityChange.Replaced<ModuleEntity>)change).getOldEntity(),
                                      ((EntityChange.Replaced<ModuleEntity>)change).getNewEntity(), storage, project);
        }
        else if (change instanceof EntityChange.Removed<ModuleEntity>) {
          snapshot.removeDependencies(((EntityChange.Removed<ModuleEntity>)change).getEntity());
        }
        else {
          throw new IllegalStateException("Unexpected change " + change.getClass());
        }
      }

      for (EntityChange<LibraryEntity> change : libraryChanges) {
        if (change instanceof EntityChange.Added<LibraryEntity>) {
          //ignore
        }
        else if (change instanceof EntityChange.Replaced<LibraryEntity>) {
          snapshot.updateLibrary(((EntityChange.Replaced<LibraryEntity>)change).getOldEntity(),
                                 ((EntityChange.Replaced<LibraryEntity>)change).getNewEntity(), storage);
        }
        else if (change instanceof EntityChange.Removed<LibraryEntity>) {
          snapshot.removeLibrary(((EntityChange.Removed<LibraryEntity>)change).getEntity().getPersistentId());
        }
        else {
          throw new IllegalStateException("Unexpected change " + change.getClass());
        }
      }

      return snapshot.toImmutableSnapshot();
    }
  }

  private static class ModifiableLibrariesSnapshot {
    private final MultiMap<LibraryId, ModuleEntity> dependencies;
    private final Map<LibraryId, IndexableSetSelfDependentOrigin> origins;

    private ModifiableLibrariesSnapshot(MultiMap<LibraryId, ModuleEntity> dependencies,
                                        Map<LibraryId, IndexableSetSelfDependentOrigin> origins) {
      this.dependencies = dependencies;
      this.origins = origins;
    }

    @NotNull
    private static ModifiableLibrariesSnapshot create(@NotNull LibrariesSnapshot snapshot) {
      MultiMap<LibraryId, ModuleEntity> dependencies = new MultiMap<>();
      for (Map.Entry<LibraryId, Collection<ModuleEntity>> entry : snapshot.dependencies.entrySet()) {
        dependencies.put(entry.getKey(), entry.getValue());
      }
      return new ModifiableLibrariesSnapshot(dependencies, new HashMap<>(snapshot.origins));
    }

    private void addDependencies(@NotNull ModuleEntity entity, @NotNull EntityStorage storage, Project project) {
      for (ModuleDependencyItem dependency : entity.getDependencies()) {
        addDependency(entity, dependency, storage, project);
      }
    }

    private void addDependency(@NotNull ModuleEntity entity,
                               @NotNull ModuleDependencyItem dependency,
                               @NotNull EntityStorage storage,
                               @NotNull Project project) {
      if (dependency instanceof ModuleDependencyItem.Exportable.LibraryDependency) {
        @NotNull LibraryId libraryId = ((ModuleDependencyItem.Exportable.LibraryDependency)dependency).getLibrary();
        dependencies.putValue(libraryId, entity);
        if (!origins.containsKey(libraryId)) {
          IndexableSetSelfDependentOrigin origin = createLibraryOrigin(libraryId, storage, project);
          if (origin != null) {
            origins.put(libraryId, origin);
          }
        }
      }
    }

    private void removeDependencies(ModuleEntity entity) {
      for (ModuleDependencyItem dependency : entity.getDependencies()) {
        if (dependency instanceof ModuleDependencyItem.Exportable.LibraryDependency) {
          LibraryId libraryId = ((ModuleDependencyItem.Exportable.LibraryDependency)dependency).getLibrary();
          dependencies.remove(libraryId, entity);
          if (!dependencies.containsKey(libraryId)) {
            origins.remove(libraryId);
          }
        }
      }
    }

    private void changeDependencies(@NotNull ModuleEntity oldEntity,
                                    @NotNull ModuleEntity newEntity,
                                    @NotNull EntityStorageSnapshot storage,
                                    @NotNull Project project) {
      SmartList<LibraryId> idsToRemove = new SmartList<>();
      for (ModuleDependencyItem dependency : oldEntity.getDependencies()) {
        if (dependency instanceof ModuleDependencyItem.Exportable.LibraryDependency) {
          LibraryId libraryId = ((ModuleDependencyItem.Exportable.LibraryDependency)dependency).getLibrary();
          dependencies.remove(libraryId, oldEntity);
          idsToRemove.add(libraryId);
        }
      }

      for (ModuleDependencyItem dependency : newEntity.getDependencies()) {
        if (dependency instanceof ModuleDependencyItem.Exportable.LibraryDependency) {
          LibraryId libraryId = ((ModuleDependencyItem.Exportable.LibraryDependency)dependency).getLibrary();
          dependencies.putValue(libraryId, newEntity);
          idsToRemove.remove(libraryId);
          if (!origins.containsKey(libraryId)) {
            IndexableSetSelfDependentOrigin origin = createLibraryOrigin(libraryId, storage, project);
            if (origin != null) {
              origins.put(libraryId, origin);
            }
          }
        }
      }

      for (LibraryId id : idsToRemove) {
        if (!dependencies.containsKey(id)) {
          origins.remove(id);
        }
      }
    }

    private void addLibrary(@NotNull Library library) {
      origins.put(LibraryEntityUtils.findLibraryId(library), createLibraryOrigin(library));
    }

    private void updateLibrary(@NotNull LibraryEntity oldEntity,
                               @NotNull LibraryEntity newEntity,
                               @NotNull EntityStorageSnapshot storage) {
      //LibraryId oldId = oldEntity.getPersistentId();
      LibraryId newId = newEntity.getPersistentId();
      //if (!oldId.equals(newId)) {
      //  todo[lene] test rename of a lib
      //}
      IndexableSetSelfDependentOrigin origin = createLibraryOrigin(newEntity, storage);
      if (origin != null) {
        origins.put(newId, origin);
      }
    }

    private void removeLibrary(@NotNull LibraryId libraryId) {
      dependencies.remove(libraryId);
      origins.remove(libraryId);
    }

    @NotNull
    private LibrariesSnapshot toImmutableSnapshot() {
      return new LibrariesSnapshot(ImmutableMap.copyOf(dependencies.entrySet()), ImmutableMap.copyOf(origins));
    }

    @Nullable
    private static IndexableSetSelfDependentOrigin createLibraryOrigin(@NotNull LibraryId libraryId,
                                                                       @NotNull EntityStorage storage,
                                                                       @NotNull Project project) {
      Library library = LibraryEntityUtils.findLibraryBridge(libraryId, storage, project);
      return createLibraryOrigin(library);
    }

    @Nullable
    private static IndexableSetSelfDependentOrigin createLibraryOrigin(@NotNull LibraryEntity libraryEntity,
                                                                       @NotNull EntityStorage storage) {
      Library library = LibraryEntityUtils.findLibraryBridge(libraryEntity, storage);
      return createLibraryOrigin(library);
    }

    @Contract("null->null;!null -> !null")
    private static LibrarySelfDependentOriginImpl createLibraryOrigin(Library library) {
      if (library == null) {
        return null;
      }
      List<VirtualFile> classFiles = LibraryIndexableFilesIteratorImpl.Companion.collectFiles(library, OrderRootType.CLASSES, null);
      List<VirtualFile> sourceFiles = LibraryIndexableFilesIteratorImpl.Companion.collectFiles(library, OrderRootType.SOURCES, null);
      return new LibrarySelfDependentOriginImpl(classFiles, sourceFiles, Arrays.asList(((LibraryEx)library).getExcludedRoots()));
    }
  }

  private static class SdkSnapshot {
    private final ImmutableMap<Sdk, IndexableSetSelfDependentOrigin> origins;

    private SdkSnapshot(ImmutableMap<Sdk, IndexableSetSelfDependentOrigin> origins) {
      this.origins = origins;
    }

    private Collection<? extends IndexableSetSelfDependentOrigin> getOrigins() {
      return origins.values();
    }

    private static SdkSnapshot create(@NotNull Collection<SdkId> sdkIds) {
      ImmutableMap.Builder<Sdk, IndexableSetSelfDependentOrigin> builder = ImmutableMap.builder();
      for (SdkId id : sdkIds) {
        Sdk sdk = ModifiableSdkSnapshot.findSdk(id);
        if (sdk != null) {
          builder.put(sdk, ModifiableSdkSnapshot.createSdkOrigin(sdk));
        }
      }
      return new SdkSnapshot(builder.build());
    }
  }

  private static class ModifiableSdkSnapshot {
    private final Map<Sdk, IndexableSetSelfDependentOrigin> origins;

    private ModifiableSdkSnapshot(Map<Sdk, IndexableSetSelfDependentOrigin> origins) {
      this.origins = origins;
    }

    @NotNull
    private static WorkspaceModelSnapshot.ModifiableSdkSnapshot create(@NotNull SdkSnapshot snapshot) {
      return new WorkspaceModelSnapshot.ModifiableSdkSnapshot(new HashMap<>(snapshot.origins));
    }

    private void addSdk(@NotNull Sdk sdk) {
      if (!origins.containsKey(sdk)) {
        IndexableSetSelfDependentOrigin origin = createSdkOrigin(sdk);
        origins.put(sdk, origin);
      }
    }

    private void updateSdk(@NotNull Sdk sdk) {
      //todo[lene] write a test
    }

    private void removeSdk(@NotNull Sdk sdk) {
      origins.remove(sdk);
    }

    private SdkSnapshot toImmutableSnapshot() {
      return new SdkSnapshot(ImmutableMap.copyOf(origins));
    }

    @NotNull
    private static IndexableSetSelfDependentOrigin createSdkOrigin(@NotNull Sdk sdk) {
      Collection<VirtualFile> rootsToIndex = SdkIndexableFilesIteratorImpl.Companion.getRootsToIndex(sdk);
      return new SdkSelfDependentOriginImpl(sdk, rootsToIndex);
    }

    @Nullable
    private static Sdk findSdk(@NotNull SdkId sdkId) {
      return ModifiableRootModelBridge.findSdk(sdkId.name, sdkId.type);
    }
  }
}