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
import com.intellij.util.indexing.roots.IndexableEntityInducedChangesProvider.OriginChange;
import com.intellij.util.indexing.roots.kind.IndexableSetIterableOrigin;
import com.intellij.util.indexing.roots.origin.LibraryIterableOriginImpl;
import com.intellij.util.indexing.roots.origin.SdkIterableOriginImpl;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryEntityUtils;
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableRootModelBridge;
import com.intellij.workspaceModel.storage.*;
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryId;
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleDependencyItem;
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.indexing.roots.IndexableEntityInducedChangesProvider.OriginAction.RemoveOrigin;
import static com.intellij.util.indexing.roots.IndexableEntityInducedChangesProvider.OriginAction.SetOrigin;

class WorkspaceModelSnapshot {
  private static volatile Map<Class<? extends WorkspaceEntity>, IndexableEntityProvider.ExistingEx<? extends WorkspaceEntity>> GENERATORS;
  private final ImmutableMap<EntityReference<? extends WorkspaceEntity>, IndexableSetIterableOrigin> entitiesToOrigins;
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

  private static Map<Class<? extends WorkspaceEntity>, IndexableEntityProvider.ExistingEx<? extends WorkspaceEntity>> createGenerators() {
    Map<Class<? extends WorkspaceEntity>, IndexableEntityProvider.ExistingEx<? extends WorkspaceEntity>> map = new HashMap<>();
    for (IndexableEntityProvider<? extends WorkspaceEntity> provider : IndexableEntityProvider.EP_NAME.getExtensionList()) {
      if (!(provider instanceof IndexableEntityProvider.ExistingEx<? extends WorkspaceEntity>)) {
        continue;
      }
      Class<? extends WorkspaceEntity> entityClass = provider.getEntityClass();
      IndexableEntityProvider.ExistingEx<? extends WorkspaceEntity> otherProvider =
        map.put(entityClass, (IndexableEntityProvider.ExistingEx<? extends WorkspaceEntity>)provider);
      if (otherProvider != null) {
        throw new IllegalStateException("Multiple IndexableEntityProvider.ExistingEx providers for entity class " + entityClass + ": " +
                                        otherProvider.getClass() + ", " + provider.getClass());
      }
    }
    return Map.copyOf(map);
  }

  static {
    GENERATORS = createGenerators();
    IndexableEntityProvider.EP_NAME.addChangeListener(() -> {
      GENERATORS = createGenerators();
    }, null);
  }

  private WorkspaceModelSnapshot(@NotNull ImmutableMap.Builder<EntityReference<? extends WorkspaceEntity>, IndexableSetIterableOrigin> builder,
                                 @NotNull LibrariesSnapshot librariesSnapshot,
                                 @NotNull SdkSnapshot sdkSnapshot) {
    this(builder.build(), librariesSnapshot, sdkSnapshot);
  }

  private WorkspaceModelSnapshot(@NotNull ImmutableMap<EntityReference<? extends WorkspaceEntity>, IndexableSetIterableOrigin> entitiesToOrigins,
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
  private static ImmutableMap.Builder<EntityReference<? extends WorkspaceEntity>, IndexableSetIterableOrigin> createBuilder(@NotNull Project project,
                                                                                                                            @NotNull EntityStorage storage) {
    ImmutableMap.Builder<EntityReference<? extends WorkspaceEntity>, IndexableSetIterableOrigin> builder =
      new ImmutableMap.Builder<>();
    for (IndexableEntityProvider.ExistingEx<?> provider : GENERATORS.values()) {
      handleProvider((IndexableEntityProvider.ExistingEx<?>)provider, storage, project, builder);
    }
    return builder;
  }

  @NotNull
  Collection<? extends IndexableSetIterableOrigin> getOrigins() {
    List<IndexableSetIterableOrigin> result = new ArrayList<>(libraries.getOrigins());
    result.addAll(sdks.getOrigins());
    result.addAll(entitiesToOrigins.values());
    return result;
  }

  private static <E extends WorkspaceEntity> void handleProvider(@NotNull IndexableEntityProvider.ExistingEx<E> provider,
                                                                 @NotNull EntityStorage storage,
                                                                 @NotNull Project project,
                                                                 @NotNull ImmutableMap.Builder<EntityReference<? extends WorkspaceEntity>, IndexableSetIterableOrigin> entities) {
    Class<E> aClass = provider.getEntityClass();
    for (E entity : SequencesKt.asIterable(storage.entities(aClass))) {
      addOrigins(entities, entity, provider, storage, project);
    }
  }

  @Nullable
  WorkspaceModelSnapshot createChangedIfNeeded(@NotNull VersionedStorageChange storageChange,
                                               @NotNull Project project) {
    EntityStorage storage = storageChange.getStorageAfter();
    Map<EntityReference<? extends WorkspaceEntity>, OriginChange> entitiesToRegenerate = new HashMap<>();
    Map<Class<? extends WorkspaceEntity>, IndexableEntityProvider.ExistingEx<? extends WorkspaceEntity>> generators = GENERATORS;
    for (EntityChange<?> change : SequencesKt.asIterable(storageChange.getAllChanges())) {
      Class<? extends WorkspaceEntity> entityInterface = getEntityInterface(change);
      IndexableEntityProvider.ExistingEx<? extends WorkspaceEntity> provider = generators.get(entityInterface);
      if (provider != null) {
        WorkspaceEntity newEntity = change.getNewEntity();
        if (newEntity != null) {
          entitiesToRegenerate.put(newEntity.createReference(), new OriginChange(newEntity, SetOrigin));
        }
        else {
          WorkspaceEntity oldEntity = change.getOldEntity();
          entitiesToRegenerate.put(Objects.requireNonNull(oldEntity).createReference(), new OriginChange(oldEntity, RemoveOrigin));
        }
      }
      for (IndexableEntityInducedChangesProvider<? extends WorkspaceEntity> changesProvider : IndexableEntityInducedChangesProvider.EP_NAME.getExtensionList()) {
        if (entityInterface.equals(changesProvider.getEntityInterface())) {
          handleInducedChanges(entitiesToRegenerate, change, storage, changesProvider, generators);
        }
      }
    }


    LibrariesSnapshot changedLibraries = libraries.createChangedIfNeeded(storageChange, storage, project);
    if (entitiesToRegenerate.isEmpty() && changedLibraries == null) {
      return null;
    }
    ImmutableMap<EntityReference<? extends WorkspaceEntity>, IndexableSetIterableOrigin>
      copy = mergeRegeneratedEntities(entitiesToOrigins, entitiesToRegenerate, generators, project, storage);
    return new WorkspaceModelSnapshot(copy, changedLibraries == null ? libraries : changedLibraries, sdks);
  }

  private static ImmutableMap<EntityReference<? extends WorkspaceEntity>, IndexableSetIterableOrigin> mergeRegeneratedEntities(
    @NotNull ImmutableMap<EntityReference<? extends WorkspaceEntity>, IndexableSetIterableOrigin> entitiesToOrigins,
    @NotNull Map<EntityReference<? extends WorkspaceEntity>, OriginChange> entitiesToRegenerate,
    @NotNull Map<Class<? extends WorkspaceEntity>, IndexableEntityProvider.ExistingEx<? extends WorkspaceEntity>> generators,
    @NotNull Project project,
    @NotNull EntityStorage storage) {
    if (entitiesToRegenerate.isEmpty()) return entitiesToOrigins;
    ImmutableMap.Builder<EntityReference<? extends WorkspaceEntity>, IndexableSetIterableOrigin> copy = new ImmutableMap.Builder<>();
    for (Map.Entry<EntityReference<? extends WorkspaceEntity>, IndexableSetIterableOrigin> entry : entitiesToOrigins.entrySet()) {
      EntityReference<? extends WorkspaceEntity> entityReference = entry.getKey();
      OriginChange originChange = entitiesToRegenerate.remove(entityReference);
      if (originChange == null) {
        copy.put(entry);
      }
      else if (originChange.action() == SetOrigin) {
        addGeneratedOrigin(project, storage, generators, copy, entityReference, originChange.entity());
      }
    }
    for (Map.Entry<EntityReference<? extends WorkspaceEntity>, OriginChange> entry : entitiesToRegenerate.entrySet()) {
      if (entry.getValue().action() == SetOrigin) {
        addGeneratedOrigin(project, storage, generators, copy, entry.getKey(), entry.getValue().entity());
      }
    }
    return copy.build();
  }

  private static <E extends WorkspaceEntity> void addGeneratedOrigin(@NotNull Project project,
                                                                     @NotNull EntityStorage storage,
                                                                     @NotNull Map<Class<? extends WorkspaceEntity>, IndexableEntityProvider.ExistingEx<? extends WorkspaceEntity>> generators,
                                                                     @NotNull ImmutableMap.Builder<EntityReference<? extends WorkspaceEntity>, IndexableSetIterableOrigin> copy,
                                                                     @NotNull EntityReference<? extends E> entityReference,
                                                                     @NotNull E entity) {
    //noinspection unchecked
    IndexableEntityProvider.ExistingEx<E> provider = (IndexableEntityProvider.ExistingEx<E>)generators.get(entity.getEntityInterface());
    IndexableSetIterableOrigin origin = provider.getExistingEntityIteratorOrigins(entity, storage, project);
    if (origin != null) {
      copy.put(entityReference, origin);
    }
  }

  private static <E extends WorkspaceEntity> void handleInducedChanges(@NotNull Map<EntityReference<? extends WorkspaceEntity>, OriginChange> entitiesToRegenerate,
                                                                       @NotNull EntityChange<E> change,
                                                                       @NotNull EntityStorage storageAfter,
                                                                       @NotNull IndexableEntityInducedChangesProvider<?> changesProvider,
                                                                       @NotNull Map<Class<? extends WorkspaceEntity>, IndexableEntityProvider.ExistingEx<? extends WorkspaceEntity>> generators) {
    //noinspection unchecked
    Collection<OriginChange> inducedChanges = ((IndexableEntityInducedChangesProvider<E>)changesProvider).getInducedChanges(change, storageAfter);
    for (OriginChange inducedChange : inducedChanges) {
      if (generators.containsKey(inducedChange.entity().getEntityInterface())) {
        entitiesToRegenerate.put(inducedChange.entity().createReference(), inducedChange);
      }
    }
  }

  private static <E extends WorkspaceEntity> void handleInducedChanges(@NotNull Map<EntityReference<? extends WorkspaceEntity>, OriginChange> entitiesToRegenerate,
                                                                       @NotNull E entity,
                                                                       @NotNull IndexableEntityInducedChangesProvider<?> changesProvider,
                                                                       @NotNull Map<Class<? extends WorkspaceEntity>, IndexableEntityProvider.ExistingEx<? extends WorkspaceEntity>> generators) {
    //noinspection unchecked
    Collection<OriginChange> changes = ((IndexableEntityInducedChangesProvider<E>)changesProvider).getInducedChangesFromRefresh(entity);
    for (OriginChange providerChange : changes) {
      if (generators.containsKey(providerChange.entity().getEntityInterface())) {
        entitiesToRegenerate.put(providerChange.entity().createReference(), providerChange);
      }
    }
  }

  @NotNull
  private static Class<? extends WorkspaceEntity> getEntityInterface(EntityChange<?> change) {
    if (change instanceof EntityChange.Added<?>) {
      return ((EntityChange.Added<?>)change).getEntity().getEntityInterface();
    }
    else if (change instanceof EntityChange.Replaced<?>) {
      return change.getNewEntity().getEntityInterface();
    }
    else if (change instanceof EntityChange.Removed<?>) {
      return change.getOldEntity().getEntityInterface();
    }
    else {
      throw new IllegalStateException("Unexpected change " + change);
    }
  }

  private static <E extends WorkspaceEntity> void addOrigins(@NotNull ImmutableMap.Builder<EntityReference<? extends WorkspaceEntity>, IndexableSetIterableOrigin> entities,
                                                             @NotNull E entity,
                                                             @NotNull IndexableEntityProvider.ExistingEx<E> provider,
                                                             @NotNull EntityStorage storage,
                                                             @NotNull Project project) {
    IndexableSetIterableOrigin origin = provider.getExistingEntityIteratorOrigins(entity, storage, project);
    if (origin != null) {
      entities.put(entity.createReference(), origin);
    }
  }

  @Nullable
  WorkspaceModelSnapshot createWithRefreshedEntitiesIfNeeded(@NotNull List<? extends WorkspaceEntity> entities,
                                                             @NotNull Project project) {
    if (entities.isEmpty()) return null;

    Map<Class<? extends WorkspaceEntity>, IndexableEntityProvider.ExistingEx<? extends WorkspaceEntity>> generators = GENERATORS;
    Map<EntityReference<? extends WorkspaceEntity>, OriginChange> entitiesToRegenerate = new HashMap<>();
    for (WorkspaceEntity entity : entities) {
      Class<? extends WorkspaceEntity> entityInterface = entity.getEntityInterface();
      if (generators.containsKey(entityInterface)) {
        entitiesToRegenerate.put(entity.createReference(), new OriginChange(entity, SetOrigin));
      }

      for (IndexableEntityInducedChangesProvider<? extends WorkspaceEntity> changesProvider : IndexableEntityInducedChangesProvider.EP_NAME.getExtensionList()) {
        if (entityInterface.equals(changesProvider.getEntityInterface())) {
          handleInducedChanges(entitiesToRegenerate, entity, changesProvider, generators);
        }
      }
    }

    EntityStorage storage = WorkspaceModel.getInstance(project).getEntityStorage().getCurrent();
    ImmutableMap<EntityReference<? extends WorkspaceEntity>, IndexableSetIterableOrigin> result =
      mergeRegeneratedEntities(entitiesToOrigins, entitiesToRegenerate, generators, project, storage);
    return new WorkspaceModelSnapshot(result, libraries, sdks);
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
    private final ImmutableMap<LibraryId, IndexableSetIterableOrigin> origins;


    private LibrariesSnapshot(ImmutableMap<LibraryId, Collection<ModuleEntity>> dependencies,
                              ImmutableMap<LibraryId, IndexableSetIterableOrigin> origins) {
      this.dependencies = dependencies;
      this.origins = origins;
    }

    @NotNull
    private Collection<? extends IndexableSetIterableOrigin> getOrigins() {
      return origins.values();
    }

    private LibrariesSnapshot createChangedIfNeeded(@NotNull VersionedStorageChange storageChange,
                                                    EntityStorage storage,
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
    private final Map<LibraryId, IndexableSetIterableOrigin> origins;

    private ModifiableLibrariesSnapshot(MultiMap<LibraryId, ModuleEntity> dependencies,
                                        Map<LibraryId, IndexableSetIterableOrigin> origins) {
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
          IndexableSetIterableOrigin origin = createLibraryOrigin(libraryId, storage, project);
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
                                    @NotNull EntityStorage storage,
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
            IndexableSetIterableOrigin origin = createLibraryOrigin(libraryId, storage, project);
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
                               @NotNull EntityStorage storage) {
      //LibraryId oldId = oldEntity.getPersistentId();
      LibraryId newId = newEntity.getPersistentId();
      //if (!oldId.equals(newId)) {
      //  todo[lene] test rename of a lib
      //}
      IndexableSetIterableOrigin origin = createLibraryOrigin(newEntity, storage);
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
    private static IndexableSetIterableOrigin createLibraryOrigin(@NotNull LibraryId libraryId,
                                                                  @NotNull EntityStorage storage,
                                                                  @NotNull Project project) {
      Library library = LibraryEntityUtils.findLibraryBridge(libraryId, storage, project);
      return createLibraryOrigin(library);
    }

    @Nullable
    private static IndexableSetIterableOrigin createLibraryOrigin(@NotNull LibraryEntity libraryEntity,
                                                                  @NotNull EntityStorage storage) {
      Library library = LibraryEntityUtils.findLibraryBridge(libraryEntity, storage);
      return createLibraryOrigin(library);
    }

    @Contract("null->null;!null -> !null")
    private static LibraryIterableOriginImpl createLibraryOrigin(Library library) {
      if (library == null) {
        return null;
      }
      List<VirtualFile> classFiles = LibraryIndexableFilesIteratorImpl.Companion.collectFiles(library, OrderRootType.CLASSES, null);
      List<VirtualFile> sourceFiles = LibraryIndexableFilesIteratorImpl.Companion.collectFiles(library, OrderRootType.SOURCES, null);
      return new LibraryIterableOriginImpl(classFiles, sourceFiles, Arrays.asList(((LibraryEx)library).getExcludedRoots()),
                                           library.getName(), library.getPresentableName());
    }
  }

  private static class SdkSnapshot {
    private final ImmutableMap<Sdk, IndexableSetIterableOrigin> origins;

    private SdkSnapshot(ImmutableMap<Sdk, IndexableSetIterableOrigin> origins) {
      this.origins = origins;
    }

    private Collection<? extends IndexableSetIterableOrigin> getOrigins() {
      return origins.values();
    }

    private static SdkSnapshot create(@NotNull Collection<SdkId> sdkIds) {
      ImmutableMap.Builder<Sdk, IndexableSetIterableOrigin> builder = ImmutableMap.builder();
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
    private final Map<Sdk, IndexableSetIterableOrigin> origins;

    private ModifiableSdkSnapshot(Map<Sdk, IndexableSetIterableOrigin> origins) {
      this.origins = origins;
    }

    @NotNull
    private static WorkspaceModelSnapshot.ModifiableSdkSnapshot create(@NotNull SdkSnapshot snapshot) {
      return new WorkspaceModelSnapshot.ModifiableSdkSnapshot(new HashMap<>(snapshot.origins));
    }

    private void addSdk(@NotNull Sdk sdk) {
      if (!origins.containsKey(sdk)) {
        IndexableSetIterableOrigin origin = createSdkOrigin(sdk);
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
    private static IndexableSetIterableOrigin createSdkOrigin(@NotNull Sdk sdk) {
      Collection<VirtualFile> rootsToIndex = SdkIndexableFilesIteratorImpl.Companion.getRootsToIndex(sdk);
      return new SdkIterableOriginImpl(sdk, rootsToIndex);
    }

    @Nullable
    private static Sdk findSdk(@NotNull SdkId sdkId) {
      return ModifiableRootModelBridge.findSdk(sdkId.name, sdkId.type);
    }
  }
}