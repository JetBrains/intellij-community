// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.roots.IndexableEntityInducedChangesProvider.OriginChange;
import com.intellij.util.indexing.roots.kind.IndexableSetIterableOrigin;
import com.intellij.util.indexing.roots.origin.LibraryIterableOriginImpl;
import com.intellij.util.indexing.roots.origin.ModuleRootIterableOriginImpl;
import com.intellij.util.indexing.roots.origin.SdkIterableOriginImpl;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryEntityUtils;
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableRootModelBridge;
import com.intellij.workspaceModel.storage.*;
import com.intellij.workspaceModel.storage.bridgeEntities.*;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

import static com.intellij.util.indexing.roots.IndexableEntityInducedChangesProvider.OriginAction.RemoveOrigin;
import static com.intellij.util.indexing.roots.IndexableEntityInducedChangesProvider.OriginAction.SetOrigin;

record WorkspaceModelSnapshot(@NotNull ActualEntitiesSnapshot actualEntities,
                              @NotNull LibrariesSnapshot librariesSnapshot,
                              @NotNull SdkSnapshot sdkSnapshot) {
  private static volatile Generators GENERATORS;

  static WorkspaceModelSnapshot create(@NotNull Project project) {
    EntityStorage entityStorage = WorkspaceModel.getInstance(project).getEntityStorage().getCurrent();
    ModifiableLibrariesSnapshot snapshot = new ModifiableLibrariesSnapshot(MultiMap.createSet(), new HashMap<>());
    Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
    SdkId sdkId = (sdk == null) ? null : SdkId.create(sdk);
    ModifiableSdkSnapshot sdkSnapshot = new ModifiableSdkSnapshot(new HashMap<>(), new MultiMap<>(), sdkId,
                                                                  new ArrayList<>());
    for (ModuleEntity entity : SequencesKt.asIterable(entityStorage.entities(ModuleEntity.class))) {
      EntityReference<ModuleEntity> moduleEntityReference = entity.createReference();
      for (ModuleDependencyItem dependency : entity.getDependencies()) {
        snapshot.addDependency(dependency, moduleEntityReference, entityStorage, project);
        sdkSnapshot.addDependency(dependency, moduleEntityReference);
      }
    }
    return new WorkspaceModelSnapshot(ActualEntitiesSnapshot.create(project, entityStorage),
                                      snapshot.toImmutableSnapshot(),
                                      sdkSnapshot.toImmutableSnapshot());
  }

  static {
    GENERATORS = new Generators();
    IndexableEntityProvider.EP_NAME.addChangeListener(() -> {
      GENERATORS = new Generators();
    }, null);
  }

  private static class Generators {
    @NotNull
    private final Map<Class<? extends WorkspaceEntity>, IndexableEntityProvider.ExistingEx<? extends WorkspaceEntity>> items;

    private Generators() {
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
      items = Map.copyOf(map);
    }

    private void forEach(@NotNull Consumer<? super IndexableEntityProvider.ExistingEx<? extends WorkspaceEntity>> consumer) {
      items.values().forEach(consumer);
    }

    @Nullable
    private <E extends WorkspaceEntity> IndexableEntityProvider.ExistingEx<E> get(Class<E> entityInterface) {
      //noinspection unchecked
      return (IndexableEntityProvider.ExistingEx<E>)items.get(entityInterface);
    }
  }

  private record SdkId(String name, String type) {
    @NotNull
    static SdkId create(@NotNull ModuleDependencyItem.SdkDependency dependency) {
      return new SdkId(dependency.getSdkName(), dependency.getSdkType());
    }

    @NotNull
    static SdkId create(@NotNull Sdk sdk) {
      return new SdkId(sdk.getName(), sdk.getSdkType().getName());
    }
  }

  @NotNull
  Collection<? extends IndexableSetIterableOrigin> getOrigins() {
    List<IndexableSetIterableOrigin> result = new ArrayList<>(librariesSnapshot.getOrigins());
    result.addAll(sdkSnapshot.getOrigins());
    result.addAll(actualEntities.getOrigins());
    return result;
  }

  @Nullable
  WorkspaceModelSnapshot createChangedIfNeeded(@NotNull VersionedStorageChange storageChange,
                                               @NotNull Project project) {
    EntityStorage storage = storageChange.getStorageAfter();
    ActualEntitiesSnapshot changedEntities = actualEntities.createChangedIfNeeded(storageChange, storage, project);
    LibrariesSnapshot changedLibraries = librariesSnapshot.createChangedIfNeeded(storageChange, storage, project);
    SdkSnapshot changedSdks = sdkSnapshot.createChangedIfNeeded(storageChange);
    if (changedEntities == null && changedLibraries == null && changedSdks == null) {
      return null;
    }
    return new WorkspaceModelSnapshot(firstNotNull(changedEntities, actualEntities),
                                      firstNotNull(changedLibraries, librariesSnapshot),
                                      firstNotNull(changedSdks, sdkSnapshot));
  }

  @NotNull
  private static <S> S firstNotNull(@Nullable S changedEntities, @NotNull S actualEntities) {
    return changedEntities == null ? actualEntities : changedEntities;
  }

  @Nullable
  WorkspaceModelSnapshot createWithRefreshedEntitiesIfNeeded(@NotNull List<? extends EntityReference<WorkspaceEntity>> references,
                                                             @NotNull Project project) {
    if (references.isEmpty()) return null;

    Generators generators = GENERATORS;
    EntityStorage storage = WorkspaceModel.getInstance(project).getEntityStorage().getCurrent();
    List<WorkspaceEntity> entities = ContainerUtil.mapNotNull(references, (ref) -> ref.resolve(storage));
    ActualEntitiesSnapshot result = actualEntities.createWithRefreshedEntitiesIfNeeded(entities, generators, project, storage);
    if (result == null) return null;
    return new WorkspaceModelSnapshot(result, librariesSnapshot, sdkSnapshot);
  }

  @Nullable
  WorkspaceModelSnapshot referencedLibraryAdded(@NotNull Library library) {
    ModifiableLibrariesSnapshot snapshot = ModifiableLibrariesSnapshot.create(librariesSnapshot);
    snapshot.addLibrary(library);
    return new WorkspaceModelSnapshot(actualEntities, snapshot.toImmutableSnapshot(), sdkSnapshot);
  }

  @Nullable
  WorkspaceModelSnapshot referencedLibraryChanged(@NotNull Library library) {
    return referencedLibraryAdded(library);
  }

  @Nullable
  WorkspaceModelSnapshot referencedLibraryRemoved(@NotNull Library library) {
    ModifiableLibrariesSnapshot snapshot = ModifiableLibrariesSnapshot.create(librariesSnapshot);
    snapshot.removeLibrary(LibraryEntityUtils.findLibraryId(library));
    return new WorkspaceModelSnapshot(actualEntities, snapshot.toImmutableSnapshot(), sdkSnapshot);
  }

  @Nullable
  WorkspaceModelSnapshot referencedSdkAdded(@NotNull Sdk sdk) {
    ModifiableSdkSnapshot snapshot = ModifiableSdkSnapshot.create(sdkSnapshot);
    snapshot.addSdkOrigin(sdk);
    return new WorkspaceModelSnapshot(actualEntities, librariesSnapshot, snapshot.toImmutableSnapshot());
  }

  @Nullable
  WorkspaceModelSnapshot referencedSdkChanged(@NotNull Sdk sdk) {
    ModifiableSdkSnapshot snapshot = ModifiableSdkSnapshot.create(sdkSnapshot);
    snapshot.updateSdk(sdk);
    return new WorkspaceModelSnapshot(actualEntities, librariesSnapshot, snapshot.toImmutableSnapshot());
  }

  @Nullable
  WorkspaceModelSnapshot referencedSdkRemoved(@NotNull Sdk sdk) {
    ModifiableSdkSnapshot snapshot = ModifiableSdkSnapshot.create(sdkSnapshot);
    snapshot.removeSdkOrigin(SdkId.create(sdk));
    return new WorkspaceModelSnapshot(actualEntities, librariesSnapshot, snapshot.toImmutableSnapshot());
  }

  @Nullable
  WorkspaceModelSnapshot projectJdkChanged(@Nullable Sdk newProjectSdk) {
    SdkId sdkId = newProjectSdk == null ? null : SdkId.create(newProjectSdk);
    if (Objects.equals(sdkId, sdkSnapshot.projectSdkId)) return null;
    ModifiableSdkSnapshot snapshot = ModifiableSdkSnapshot.create(sdkSnapshot);
    snapshot.projectJdkChanged(sdkId, newProjectSdk);
    return new WorkspaceModelSnapshot(actualEntities, librariesSnapshot, snapshot.toImmutableSnapshot());
  }

  private record LibrariesSnapshot(ImmutableMap<LibraryId, Collection<EntityReference<ModuleEntity>>> dependencies,
                                   ImmutableMap<LibraryId, IndexableSetIterableOrigin> origins) {//todo[lene] write test for library rename
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
          snapshot.removeLibrary(((EntityChange.Removed<LibraryEntity>)change).getEntity().getSymbolicId());
        }
        else {
          throw new IllegalStateException("Unexpected change " + change.getClass());
        }
      }

      return snapshot.toImmutableSnapshot();
    }
    }

  private record ModifiableLibrariesSnapshot(MultiMap<LibraryId, EntityReference<ModuleEntity>> dependencies,
                                             Map<LibraryId, IndexableSetIterableOrigin> origins) {
    @NotNull
    private static ModifiableLibrariesSnapshot create(@NotNull LibrariesSnapshot snapshot) {
      MultiMap<LibraryId, EntityReference<ModuleEntity>> dependencies = new MultiMap<>();
      for (Map.Entry<LibraryId, Collection<EntityReference<ModuleEntity>>> entry : snapshot.dependencies.entrySet()) {
        dependencies.put(entry.getKey(), entry.getValue());
      }
      return new ModifiableLibrariesSnapshot(dependencies, new HashMap<>(snapshot.origins));
    }

    private void addDependencies(@NotNull ModuleEntity entity, @NotNull EntityStorage storage, Project project) {
      for (ModuleDependencyItem dependency : entity.getDependencies()) {
        addDependency(dependency, entity.createReference(), storage, project);
      }
    }

    private void addDependency(@NotNull ModuleDependencyItem dependency,
                               @NotNull EntityReference<ModuleEntity> moduleEntityReference,
                               @NotNull EntityStorage storage,
                               @NotNull Project project) {
      if (dependency instanceof ModuleDependencyItem.Exportable.LibraryDependency) {
        @NotNull LibraryId libraryId = ((ModuleDependencyItem.Exportable.LibraryDependency)dependency).getLibrary();
        dependencies.putValue(libraryId, moduleEntityReference);
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
          dependencies.remove(libraryId, entity.createReference());
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
          dependencies.remove(libraryId, oldEntity.createReference());
          idsToRemove.add(libraryId);
        }
      }

      for (ModuleDependencyItem dependency : newEntity.getDependencies()) {
        if (dependency instanceof ModuleDependencyItem.Exportable.LibraryDependency) {
          LibraryId libraryId = ((ModuleDependencyItem.Exportable.LibraryDependency)dependency).getLibrary();
          dependencies.putValue(libraryId, newEntity.createReference());
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
      //LibraryId oldId = oldEntity.getSymbolicId();
      LibraryId newId = newEntity.getSymbolicId();
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

  private record SdkSnapshot(@NotNull ImmutableMap<SdkId, IndexableSetIterableOrigin> origins,
                             @NotNull ImmutableMap<SdkId, Collection<EntityReference<ModuleEntity>>> dependencies,
                             @Nullable SdkId projectSdkId,
                             @NotNull Collection<EntityReference<ModuleEntity>> projectSdkDependencies) {

    private Collection<? extends IndexableSetIterableOrigin> getOrigins() {
      return origins.values();
    }

    @Nullable
    public IndexableFilesIterator createIterator(@NotNull Sdk sdk, @Nullable Collection<? extends VirtualFile> rootsToFilter) {
      IndexableSetIterableOrigin origin = Objects.requireNonNull(origins.get(SdkId.create(sdk)), "Unknown SDK " + sdk);
      if (rootsToFilter == null) {
        return origin.createIterator();
      }
      SdkIterableOriginImpl filteredOrigin = ((SdkIterableOriginImpl)origin).copyWithFilteredRoots(rootsToFilter);
      return filteredOrigin == null ? null : filteredOrigin.createIterator();
    }

    private SdkSnapshot createChangedIfNeeded(@NotNull VersionedStorageChange storageChange) {
      List<EntityChange<ModuleEntity>> moduleChanges = storageChange.getChanges(ModuleEntity.class);

      if (moduleChanges.isEmpty()) return null;

      ModifiableSdkSnapshot snapshot = ModifiableSdkSnapshot.create(this);

      for (EntityChange<ModuleEntity> change : moduleChanges) {
        ModuleEntity oldEntity = change.getOldEntity();
        if (oldEntity != null) {
          snapshot.removeDependencies(oldEntity);
        }
        ModuleEntity newEntity = change.getNewEntity();
        if (newEntity != null) {
          snapshot.addDependencies(newEntity);
        }
      }

      return snapshot.toImmutableSnapshot();
    }
  }

  private static class ModifiableSdkSnapshot {
    private final Map<SdkId, IndexableSetIterableOrigin> origins;
    private final MultiMap<SdkId, EntityReference<ModuleEntity>> dependencies;
    @Nullable
    private SdkId projectSdkId;
    private final Collection<EntityReference<ModuleEntity>> projectSdkDependencies;

    private ModifiableSdkSnapshot(@NotNull Map<SdkId, IndexableSetIterableOrigin> origins,
                                  @NotNull MultiMap<SdkId, EntityReference<ModuleEntity>> dependencies,
                                  @Nullable SdkId projectSdkId,
                                  @NotNull Collection<EntityReference<ModuleEntity>> projectSdkDependencies) {
      this.origins = origins;
      this.dependencies = dependencies;
      this.projectSdkId = projectSdkId;
      this.projectSdkDependencies = projectSdkDependencies;
    }

    @NotNull
    private static ModifiableSdkSnapshot create(@NotNull SdkSnapshot snapshot) {
      MultiMap<SdkId, EntityReference<ModuleEntity>> dependencies = new MultiMap<>();
      for (Map.Entry<SdkId, Collection<EntityReference<ModuleEntity>>> entry : snapshot.dependencies.entrySet()) {
        dependencies.put(entry.getKey(), entry.getValue());
      }
      return new ModifiableSdkSnapshot(new HashMap<>(snapshot.origins), dependencies, snapshot.projectSdkId,
                                       new ArrayList<>(snapshot.projectSdkDependencies));
    }

    private void addSdkOrigin(@NotNull Sdk sdk) {
      addSdkOrigin(SdkId.create(sdk), sdk);
    }

    private void addSdkOrigin(@NotNull SdkId sdkId, @Nullable Sdk sdk) {
      if (sdk != null && !origins.containsKey(sdkId)) {
        IndexableSetIterableOrigin origin = createSdkOrigin(sdk);
        origins.put(sdkId, origin);
      }
    }

    private void addSdkOrigin(@NotNull SdkId sdkId) {
      addSdkOrigin(sdkId, findSdk(sdkId));
    }

    private void updateSdk(@NotNull Sdk sdk) {//todo[lene] write a test on sdk rename
      SdkId sdkId = SdkId.create(sdk);
      if (origins.containsKey(sdkId)) {
        IndexableSetIterableOrigin origin = createSdkOrigin(sdk);
        origins.put(sdkId, origin);
      }
    }

    private void removeSdkOrigin(@NotNull SdkId sdkId) {
      origins.remove(sdkId);
    }

    private void addDependency(@NotNull ModuleDependencyItem dependency, @NotNull EntityReference<ModuleEntity> entityReference) {
      if (dependency instanceof ModuleDependencyItem.SdkDependency) {
        SdkId sdkId = SdkId.create((ModuleDependencyItem.SdkDependency)dependency);
        boolean newSdk = !hasDependency(sdkId);
        dependencies.putValue(sdkId, entityReference);
        if (newSdk) {
          addSdkOrigin(sdkId);
        }
      }
      else if (dependency instanceof ModuleDependencyItem.InheritedSdkDependency) {
        boolean newSdk = projectSdkId != null && !hasDependency(projectSdkId);
        projectSdkDependencies.add(entityReference);
        if (newSdk) {
          addSdkOrigin(projectSdkId);
        }
      }
    }

    private void addDependencies(@NotNull ModuleEntity entity) {
      EntityReference<ModuleEntity> reference = entity.createReference();
      for (ModuleDependencyItem dependency : entity.getDependencies()) {
        addDependency(dependency, reference);
      }
    }

    private void removeDependency(@NotNull ModuleDependencyItem dependency, @NotNull EntityReference<ModuleEntity> entityReference) {
      if (dependency instanceof ModuleDependencyItem.SdkDependency) {
        SdkId sdkId = SdkId.create((ModuleDependencyItem.SdkDependency)dependency);
        dependencies.remove(sdkId, entityReference);
        if (!hasDependency(sdkId)) {
          removeSdkOrigin(sdkId);
        }
      }
      else if (dependency instanceof ModuleDependencyItem.InheritedSdkDependency) {
        projectSdkDependencies.remove(entityReference);
        if (projectSdkId != null && !hasDependency(projectSdkId)) {
          removeSdkOrigin(projectSdkId);
        }
      }
    }

    private boolean hasDependency(@NotNull SdkId sdkId) {
      if (!dependencies.get(sdkId).isEmpty()) return true;
      if (sdkId.equals(projectSdkId) && !projectSdkDependencies.isEmpty()) return true;
      return false;
    }

    private void removeDependencies(@NotNull ModuleEntity entity) {
      EntityReference<ModuleEntity> reference = entity.createReference();
      for (ModuleDependencyItem dependency : entity.getDependencies()) {
        removeDependency(dependency, reference);
      }
    }

    private void projectJdkChanged(@Nullable SdkId sdkId, @Nullable Sdk newProjectSdk) {
      SdkId oldProjectSdkId = projectSdkId;
      projectSdkId = sdkId;
      if (oldProjectSdkId != null && !hasDependency(oldProjectSdkId)) {
        removeSdkOrigin(oldProjectSdkId);
      }
      if (sdkId != null && hasDependency(sdkId)) {
        addSdkOrigin(sdkId, newProjectSdk);
      }
    }

    private SdkSnapshot toImmutableSnapshot() {
      return new SdkSnapshot(ImmutableMap.copyOf(origins), ImmutableMap.copyOf(dependencies.entrySet()),
                             projectSdkId, ImmutableSet.copyOf(projectSdkDependencies));
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

  private record ActualEntitiesSnapshot(
    @NotNull ImmutableMap<EntityReference<? extends WorkspaceEntity>, IndexableSetIterableOrigin> entitiesToOrigins,
    @NotNull ImmutableMap<EntityReference<ContentRootEntity>, ModuleRootIterableOriginImpl> contentRootEntitiesToOrigins) {

    private Collection<? extends IndexableSetIterableOrigin> getOrigins() {
      ArrayList<IndexableSetIterableOrigin> origins = new ArrayList<>(entitiesToOrigins.values());
      origins.addAll(contentRootEntitiesToOrigins.values());
      return origins;
    }

    @NotNull
    private static ActualEntitiesSnapshot create(@NotNull Project project,
                                                 @NotNull EntityStorage storage) {
      ImmutableMap.Builder<EntityReference<? extends WorkspaceEntity>, IndexableSetIterableOrigin> builder =
        new ImmutableMap.Builder<>();
      Collection<ContentRootDescription> descriptions = new ArrayList<>();
      GENERATORS.forEach(provider -> handleProvider(provider, storage, project, builder, descriptions));
      return new ActualEntitiesSnapshot(builder.build(), buildContentRootsMap(descriptions));
    }

    private static <E extends WorkspaceEntity> void handleProvider(@NotNull IndexableEntityProvider.ExistingEx<E> provider,
                                                                   @NotNull EntityStorage storage,
                                                                   @NotNull Project project,
                                                                   @NotNull ImmutableMap.Builder<EntityReference<? extends WorkspaceEntity>, IndexableSetIterableOrigin> entities,
                                                                   @NotNull Collection<? super ContentRootDescription> descriptions) {
      Class<E> aClass = provider.getEntityClass();
      if (ContentRootEntity.class.equals(aClass)) {
        for (E entity : SequencesKt.asIterable(storage.entities(aClass))) {
          IndexableSetIterableOrigin origin = provider.getExistingEntityIteratorOrigins(entity, storage, project);
          if (origin != null) {
            descriptions.add(new ContentRootDescription(entity.createReference(), (ModuleRootIterableOriginImpl)origin));
          }
        }
      }
      else {
        for (E entity : SequencesKt.asIterable(storage.entities(aClass))) {
          IndexableSetIterableOrigin origin = provider.getExistingEntityIteratorOrigins(entity, storage, project);
          if (origin != null) {
            entities.put(entity.createReference(), origin);
          }
        }
      }
    }

    private static ImmutableMap<EntityReference<ContentRootEntity>, ModuleRootIterableOriginImpl> buildContentRootsMap(
      @NotNull Collection<ContentRootDescription> descriptions) {
      Map<VirtualFile, ContentRootDescription> rootMap = ContainerUtil.map2Map(descriptions, description -> {
        List<VirtualFile> roots = description.origin.getRoots();
        assert roots.size() == 1 : "Too many roots for a ContentRoot " + roots;
        return new Pair<>(roots.get(0), description);
      });

      for (Map.Entry<VirtualFile, ContentRootDescription> entry : rootMap.entrySet()) {
        VirtualFile parent = entry.getKey().getParent();
        while (parent != null) {
          ContentRootDescription parentDescription = rootMap.get(parent);
          if (parentDescription != null) {
            parentDescription.childContentRoots.add(entry.getKey());
            break;
          }
          parent = parent.getParent();
        }
      }

      ImmutableMap.Builder<EntityReference<ContentRootEntity>, ModuleRootIterableOriginImpl> copy = new ImmutableMap.Builder<>();
      for (ContentRootDescription description : descriptions) {
        copy.put(description.reference, description.origin.copyWithChildContentRoots(description.childContentRoots));
      }
      return copy.build();
    }

    @Nullable
    private ActualEntitiesSnapshot mergeRegeneratedEntities(
      @NotNull EntitiesToRegenerate entitiesToRegenerate,
      @NotNull Project project,
      @NotNull EntityStorage storage) {
      ImmutableMap<EntityReference<? extends WorkspaceEntity>, IndexableSetIterableOrigin> merge =
        merge(entitiesToRegenerate.items, project, storage);
      ImmutableMap<EntityReference<ContentRootEntity>, ModuleRootIterableOriginImpl> mergeContentRoots =
        mergeContentRoots(entitiesToRegenerate.contentRootItems, project, storage);
      if (merge == null && mergeContentRoots == null) return null;
      return new ActualEntitiesSnapshot(merge == null ? entitiesToOrigins : merge,
                                        mergeContentRoots == null ? contentRootEntitiesToOrigins : mergeContentRoots);
    }

    @Nullable
    public ImmutableMap<EntityReference<? extends WorkspaceEntity>, IndexableSetIterableOrigin> merge(
      @NotNull Map<EntityReference<? extends WorkspaceEntity>, ChangeInformation<? extends WorkspaceEntity>> items,
      @NotNull Project project,
      @NotNull EntityStorage storage) {
      if (items.isEmpty()) return null;
      ImmutableMap.Builder<EntityReference<? extends WorkspaceEntity>, IndexableSetIterableOrigin> copy =
        new ImmutableMap.Builder<>();
      for (Map.Entry<EntityReference<? extends WorkspaceEntity>, IndexableSetIterableOrigin> entry : entitiesToOrigins.entrySet()) {
        EntityReference<? extends WorkspaceEntity> entityReference = entry.getKey();
        ChangeInformation<?> changeInformation = items.remove(entityReference);
        if (changeInformation == null) {
          copy.put(entry);
        }
        else if (changeInformation.action() == SetOrigin) {
          IndexableSetIterableOrigin origin = changeInformation.generateOrigin(project, storage);
          if (origin != null) {
            copy.put(changeInformation.reference, origin);
          }
        }
      }
      for (ChangeInformation<? extends WorkspaceEntity> changeInformation : items.values()) {
        if (changeInformation.action() == SetOrigin) {
          IndexableSetIterableOrigin origin = changeInformation.generateOrigin(project, storage);
          if (origin != null) {
            copy.put(changeInformation.reference, origin);
          }
        }
      }
      return copy.build();
    }

    @Nullable
    public ImmutableMap<EntityReference<ContentRootEntity>, ModuleRootIterableOriginImpl> mergeContentRoots(
      @NotNull Map<EntityReference<ContentRootEntity>, ChangeInformation<ContentRootEntity>> items,
      @NotNull Project project,
      @NotNull EntityStorage storage) {
      if (items.isEmpty()) return null;
      Collection<ContentRootDescription> descriptions = new ArrayList<>();
      for (Map.Entry<EntityReference<ContentRootEntity>, ModuleRootIterableOriginImpl> entry : contentRootEntitiesToOrigins.entrySet()) {
        EntityReference<ContentRootEntity> entityReference = entry.getKey();
        ChangeInformation<ContentRootEntity> changeInformation = items.remove(entityReference);
        if (changeInformation == null) {
          descriptions.add(new ContentRootDescription(entityReference, entry.getValue()));
        }
        else if (changeInformation.action() == SetOrigin) {
          IndexableSetIterableOrigin origin = changeInformation.generateOrigin(project, storage);
          if (origin != null) {
            descriptions.add(new ContentRootDescription(changeInformation.reference, (ModuleRootIterableOriginImpl)origin));
          }
        }
      }
      for (ChangeInformation<ContentRootEntity> changeInformation : items.values()) {
        if (changeInformation.action() == SetOrigin) {
          IndexableSetIterableOrigin origin = changeInformation.generateOrigin(project, storage);
          if (origin != null) {
            descriptions.add(new ContentRootDescription(changeInformation.reference, (ModuleRootIterableOriginImpl)origin));
          }
        }
      }
      return buildContentRootsMap(descriptions);
    }

    @Nullable
    private ActualEntitiesSnapshot createChangedIfNeeded(@NotNull VersionedStorageChange storageChange,
                                                         @NotNull EntityStorage storage,
                                                         @NotNull Project project) {
      EntitiesToRegenerate entitiesToRegenerate = new EntitiesToRegenerate();
      Generators generators = GENERATORS;
      for (EntityChange<?> change : SequencesKt.asIterable(storageChange.getAllChanges())) {
        WorkspaceEntity newEntity = change.getNewEntity();
        if (newEntity != null) {
          entitiesToRegenerate.putNotNullAndHandleable(newEntity, SetOrigin, generators);
        }
        else {
          entitiesToRegenerate.putNotNullAndHandleable(change.getOldEntity(), RemoveOrigin, generators);
        }
        IndexableEntityInducedChangesProvider.forEachRelevantProvider(change, (provider, providedChange) -> {
          Collection<OriginChange> inducedChanges = provider.getInducedChanges(providedChange, storage);
          entitiesToRegenerate.loadInducedChanges(inducedChanges, generators);
        });
      }
      return mergeRegeneratedEntities(entitiesToRegenerate, project, storage);
    }

    @Nullable
    private ActualEntitiesSnapshot createWithRefreshedEntitiesIfNeeded(@NotNull List<? extends WorkspaceEntity> refreshedEntities,
                                                                       @NotNull Generators generators,
                                                                       @NotNull Project project,
                                                                       @NotNull EntityStorage storage) {
      EntitiesToRegenerate entitiesToRegenerate = collectRefreshedEntitiesToRegenerate(refreshedEntities, generators);
      return mergeRegeneratedEntities(entitiesToRegenerate, project, storage);
    }

    @NotNull
    private static EntitiesToRegenerate collectRefreshedEntitiesToRegenerate(@NotNull List<? extends WorkspaceEntity> refreshedEntities,
                                                                             @NotNull Generators generators) {
      EntitiesToRegenerate entitiesToRegenerate = new EntitiesToRegenerate();
      for (WorkspaceEntity entity : refreshedEntities) {
        entitiesToRegenerate.putNotNullAndHandleable(entity, SetOrigin, generators);

        IndexableEntityInducedChangesProvider.forEachRelevantProvider(entity, provider -> {
          Collection<OriginChange> inducedChanges = provider.getInducedChangesFromRefresh(entity);
          entitiesToRegenerate.loadInducedChanges(inducedChanges, generators);
        });
      }
      return entitiesToRegenerate;
    }

    private record ContentRootDescription(@NotNull EntityReference<ContentRootEntity> reference,
                                          @NotNull ModuleRootIterableOriginImpl origin,
                                          @NotNull List<VirtualFile> childContentRoots) {
      ContentRootDescription(EntityReference<ContentRootEntity> reference, ModuleRootIterableOriginImpl origin) {
        this(reference, origin, new SmartList<>());
      }
    }

    private record ChangeInformation<E extends WorkspaceEntity>(@NotNull E entity,
                                                                @NotNull EntityReference<E> reference,
                                                                @NotNull IndexableEntityInducedChangesProvider.OriginAction action,
                                                                @NotNull IndexableEntityProvider.ExistingEx<E> provider) {
      @Nullable
      private IndexableSetIterableOrigin generateOrigin(@NotNull Project project, @NotNull EntityStorage storage) {
        return provider.getExistingEntityIteratorOrigins(entity, storage, project);
      }
    }

    private static class EntitiesToRegenerate {
      private final @NotNull Map<EntityReference<? extends WorkspaceEntity>, ChangeInformation<? extends WorkspaceEntity>> items =
        new HashMap<>();
      private final @NotNull Map<EntityReference<ContentRootEntity>, ChangeInformation<ContentRootEntity>> contentRootItems =
        new HashMap<>();

      private <E extends WorkspaceEntity> void putNotNullAndHandleable(@Nullable E entity,
                                                                       @NotNull IndexableEntityInducedChangesProvider.OriginAction action,
                                                                       @NotNull Generators generators) {
        if (entity == null) return;
        //noinspection unchecked
        IndexableEntityProvider.ExistingEx<E> provider = generators.get((Class<E>)entity.getEntityInterface());
        if (provider == null) return;
        EntityReference<E> reference = entity.createReference();
        if (entity.getEntityInterface().equals(ContentRootEntity.class)) {
          //noinspection unchecked
          contentRootItems.put((EntityReference<ContentRootEntity>)reference,
                               new ChangeInformation<>((ContentRootEntity)entity,
                                                       (EntityReference<ContentRootEntity>)reference, action,
                                                       (IndexableEntityProvider.ExistingEx<ContentRootEntity>)provider));
        }
        else {
          items.put(reference, new ChangeInformation<>(entity, reference, action, provider));
        }
      }

      private void loadInducedChanges(@NotNull Collection<OriginChange> inducedChanges, @NotNull Generators generators) {
        for (OriginChange inducedChange : inducedChanges) {
          putNotNullAndHandleable(inducedChange.entity(), inducedChange.action(), generators);
        }
      }
    }
  }
}