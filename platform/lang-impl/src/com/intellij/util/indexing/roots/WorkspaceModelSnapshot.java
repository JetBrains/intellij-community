// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.fileTypes.impl.FileTypeAssocTable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.roots.IndexableEntityInducedChangesProvider.OriginChange;
import com.intellij.util.indexing.roots.kind.IndexableSetIterableOrigin;
import com.intellij.util.indexing.roots.kind.ModuleRootOrigin;
import com.intellij.util.indexing.roots.origin.ModuleRootIterableOriginImpl;
import com.intellij.util.indexing.roots.origin.SdkIterableOriginImpl;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.ide.impl.UtilsKt;
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryEntityUtils;
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleEntityUtils;
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ContentEntryBridge;
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableRootModelBridge;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge;
import com.intellij.workspaceModel.storage.*;
import com.intellij.workspaceModel.storage.bridgeEntities.*;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory;

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
        if (!(provider instanceof IndexableEntityProvider.ExistingEx<?>)) {
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
      map.put(ContentRootEntity.class, createDummyProvider(ContentRootEntity.class));
      map.put(SourceRootEntity.class, createDummyProvider(SourceRootEntity.class));
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

    private static <E extends WorkspaceEntity> IndexableEntityProvider.ExistingEx<E> createDummyProvider(Class<E> aClass) {
      return new IndexableEntityProvider.ExistingEx<>() {
        @Override
        public @NotNull Collection<IndexableSetIterableOrigin> getExistingEntityIteratorOrigins(@NotNull E entity,
                                                                                                @NotNull EntityStorage storage,
                                                                                                @NotNull Project project) {
          return Collections.emptyList();
        }

        @Override
        public @NotNull Collection<? extends IndexableIteratorBuilder> getIteratorBuildersForExistingModule(@NotNull ModuleEntity entity,
                                                                                                            @NotNull EntityStorage entityStorage,
                                                                                                            @NotNull Project project) {
          return Collections.emptyList();
        }

        @Override
        public @NotNull Class<E> getEntityClass() {
          return aClass;
        }

        @Override
        public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull E entity,
                                                                                                      @NotNull Project project) {
          return Collections.emptyList();
        }

        @Override
        public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull E oldEntity,
                                                                                                         @NotNull E newEntity,
                                                                                                         @NotNull Project project) {
          return Collections.emptyList();
        }
      };
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

  @NotNull
  Collection<? extends VirtualFile> getExcludedRoots() {
    Set<VirtualFile> result = new HashSet<>();
    for (EntityFiles value : actualEntities.entitiesExcludedRoots) {
      result.addAll(value.files);
    }
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
    return new WorkspaceModelSnapshot(Objects.requireNonNullElse(changedEntities, actualEntities),
                                      Objects.requireNonNullElse(changedLibraries, librariesSnapshot),
                                      Objects.requireNonNullElse(changedSdks, sdkSnapshot));
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

  WorkspaceModelSnapshot referencedLibraryAdded(@NotNull Library library) {
    ModifiableLibrariesSnapshot snapshot = ModifiableLibrariesSnapshot.create(librariesSnapshot);
    snapshot.addLibrary(library);
    return new WorkspaceModelSnapshot(actualEntities, snapshot.toImmutableSnapshot(), sdkSnapshot);
  }

  WorkspaceModelSnapshot referencedLibraryChanged(@NotNull Library library) {
    return referencedLibraryAdded(library);
  }

  WorkspaceModelSnapshot referencedLibraryRemoved(@NotNull Library library) {
    ModifiableLibrariesSnapshot snapshot = ModifiableLibrariesSnapshot.create(librariesSnapshot);
    snapshot.removeLibrary(LibraryEntityUtils.findLibraryId(library));
    return new WorkspaceModelSnapshot(actualEntities, snapshot.toImmutableSnapshot(), sdkSnapshot);
  }

  WorkspaceModelSnapshot referencedSdkAdded(@NotNull Sdk sdk) {
    ModifiableSdkSnapshot snapshot = ModifiableSdkSnapshot.create(sdkSnapshot);
    snapshot.addSdkOrigin(sdk);
    return new WorkspaceModelSnapshot(actualEntities, librariesSnapshot, snapshot.toImmutableSnapshot());
  }

  WorkspaceModelSnapshot referencedSdkChanged(@NotNull Sdk sdk) {
    ModifiableSdkSnapshot snapshot = ModifiableSdkSnapshot.create(sdkSnapshot);
    snapshot.updateSdk(sdk);
    return new WorkspaceModelSnapshot(actualEntities, librariesSnapshot, snapshot.toImmutableSnapshot());
  }

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

  @NotNull
  public IndexableSetIterableOrigin getSdkOrigin(@NotNull Sdk sdk) {
    return sdkSnapshot.getSdkOrigin(sdk);
  }

  @NotNull
  static Collection<IndexableFilesIterator> createModuleIterators(@NotNull Iterable<IndexableSetIterableOrigin> origins,
                                                                  @NotNull Module module,
                                                                  @Nullable Collection<? extends VirtualFile> filter) {
    List<IndexableFilesIterator> result = new ArrayList<>();
    for (IndexableSetIterableOrigin origin : origins) {
      if (!(origin instanceof ModuleRootOrigin) || !module.equals(((ModuleRootOrigin)origin).getModule())) {
        continue;
      }
      if (filter == null) {
        result.add(origin.createIterator());
      }
      else {
        ModuleRootIterableOriginImpl copy = ((ModuleRootIterableOriginImpl)origin).copyWithFilteredRoots(filter);
        if (copy != null) {
          result.add(copy.createIterator());
        }
      }
    }
    return result;
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
    private static IndexableSetIterableOrigin createLibraryOrigin(Library library) {
      if (library == null) {
        return null;
      }
      return IterableOriginsMethods.INSTANCE.createLibraryOrigin(library);
    }
  }

  private record SdkSnapshot(@NotNull ImmutableMap<SdkId, IndexableSetIterableOrigin> origins,
                             @NotNull ImmutableMap<SdkId, Collection<EntityReference<ModuleEntity>>> dependencies,
                             @Nullable SdkId projectSdkId,
                             @NotNull Collection<EntityReference<ModuleEntity>> projectSdkDependencies) {

    private Collection<? extends IndexableSetIterableOrigin> getOrigins() {
      return origins.values();
    }

    @NotNull
    private IndexableSetIterableOrigin getSdkOrigin(@NotNull Sdk sdk) {
      return Objects.requireNonNull(origins.get(SdkId.create(sdk)));
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

  private record EntityOrigins(EntityReference<? extends WorkspaceEntity> reference, Collection<IndexableSetIterableOrigin> origins) {
  }

  private record ContentRootOrigin(EntityReference<ContentRootEntity> reference, ModuleRootIterableOriginImpl origin) {
  }

  private record SourceRootOrigin(EntityReference<SourceRootEntity> reference, ModuleRootIterableOriginImpl origin) {
  }

  private record EntityFiles(EntityReference<? extends WorkspaceEntity> reference, Collection<VirtualFile> files) {
  }

  private record RootsOrigins(ImmutableList<ContentRootOrigin> contentRootEntitiesOrigins,
                              ImmutableList<SourceRootOrigin> sourceRootEntitiesOrigins) {

  }

  private record ActualEntitiesSnapshot(
    @NotNull ImmutableList<EntityOrigins> entitiesOrigins,
    @NotNull RootsOrigins rootOrigins,
    @NotNull ImmutableList<ContentRootDescription> contentRootDescriptions,
    @NotNull ImmutableList<SourceRootDescription> sourceRootDescriptions,
    @NotNull ImmutableList<EntityFiles> entitiesExcludedRoots) {

    private Collection<? extends IndexableSetIterableOrigin> getOrigins() {
      ArrayList<IndexableSetIterableOrigin> origins = new ArrayList<>();
      for (EntityOrigins value : entitiesOrigins) {
        origins.addAll(value.origins);
      }
      for (ContentRootOrigin origin : rootOrigins.contentRootEntitiesOrigins) {
        origins.add(origin.origin);
      }
      for (SourceRootOrigin origin : rootOrigins.sourceRootEntitiesOrigins) {
        origins.add(origin.origin);
      }
      return origins;
    }

    @NotNull
    private static ActualEntitiesSnapshot create(@NotNull Project project,
                                                 @NotNull EntityStorage storage) {
      ImmutableList.Builder<EntityOrigins> builder = new ImmutableList.Builder<>();
      List<ContentRootDescription> contentRoots = new ArrayList<>();
      List<SourceRootDescription> sourceRoots = new ArrayList<>();
      ImmutableList.Builder<EntityFiles> excludedBuilder = new ImmutableList.Builder<>();
      GENERATORS.forEach(provider -> handleProvider(provider, storage, project, builder, contentRoots, sourceRoots, excludedBuilder));
      return new ActualEntitiesSnapshot(builder.build(), buildRootsOrigins(project, contentRoots, sourceRoots),
                                        ImmutableList.copyOf(contentRoots), ImmutableList.copyOf(sourceRoots),
                                        excludedBuilder.build());
    }

    private static <E extends WorkspaceEntity> void handleProvider(@NotNull IndexableEntityProvider.ExistingEx<E> provider,
                                                                   @NotNull EntityStorage storage,
                                                                   @NotNull Project project,
                                                                   @NotNull ImmutableList.Builder<EntityOrigins> entities,
                                                                   @NotNull Collection<? super ContentRootDescription> contentRootDescriptions,
                                                                   @NotNull Collection<SourceRootDescription> sourceRootDescriptions,
                                                                   @NotNull ImmutableList.Builder<EntityFiles> excludedBuilder) {
      Class<E> aClass = provider.getEntityClass();
      if (ContentRootEntity.class.equals(aClass)) {
        for (ContentRootEntity entity : SequencesKt.asIterable(storage.entities(ContentRootEntity.class))) {
          ContainerUtil.addIfNotNull(contentRootDescriptions, createContentRootDescription(entity, storage));
        }
      }
      else if (SourceRootEntity.class.equals(aClass)) {
        for (SourceRootEntity entity : SequencesKt.asIterable(storage.entities(SourceRootEntity.class))) {
          ContainerUtil.addIfNotNull(sourceRootDescriptions, createSourceRootDescription(entity, storage));
        }
      }
      else {
        for (E entity : SequencesKt.asIterable(storage.entities(aClass))) {
          Collection<IndexableSetIterableOrigin> origins = provider.getExistingEntityIteratorOrigins(entity, storage, project);
          if (!origins.isEmpty()) {
            entities.add(new EntityOrigins(entity.createReference(), origins));
          }
          Collection<VirtualFile> excludedRoots = provider.getExcludedRoots(entity, storage, project);
          if (!excludedRoots.isEmpty()) {
            excludedBuilder.add(new EntityFiles(entity.createReference(), excludedRoots));
          }
        }
      }
    }

    @Nullable
    private static ContentRootDescription createContentRootDescription(@NotNull ContentRootEntity entity, @NotNull EntityStorage storage) {
      ModuleBridge module = ModuleEntityUtils.findModule(entity.getModule(), storage);
      if (module == null) {
        return null;
      }
      VirtualFile root = UtilsKt.getVirtualFile(entity.getUrl());
      if (root == null) return null;
      return new ContentRootDescription(entity.createReference(), entity.getModule().createReference(), module, root,
                                        IndexableEntityProviderMethods.INSTANCE.getExcludedFiles(entity),
                                        entity.getExcludedPatterns());
    }

    @Nullable
    private static SourceRootDescription createSourceRootDescription(@NotNull SourceRootEntity entity, @NotNull EntityStorage storage) {
      ContentRootEntity contentRoot = entity.getContentRoot();
      ModuleBridge module = ModuleEntityUtils.findModule(contentRoot.getModule(), storage);
      if (module == null) {
        return null;
      }
      VirtualFile root = UtilsKt.getVirtualFile(entity.getUrl());
      if (root == null) return null;
      return new SourceRootDescription(entity.createReference(), contentRoot.createReference(), contentRoot.getModule().createReference(),
                                       module, root, UtilsKt.getVirtualFile(contentRoot.getUrl()),
                                       IndexableEntityProviderMethods.INSTANCE.getExcludedFiles(contentRoot),
                                       contentRoot.getExcludedPatterns());
    }

    private static RootsOrigins buildRootsOrigins(@NotNull Project project,
                                                  @NotNull List<ContentRootDescription> allDescriptions,
                                                  @NotNull List<SourceRootDescription> sourceRoots) {

      record PreIterator(@NotNull VirtualFile root,
                         @NotNull ContentRootDescription description,
                         @NotNull Collection<VirtualFile> childContentRoots) {
        PreIterator(@NotNull ContentRootDescription description) {
          this(description.root, description, new SmartList<>());
        }
      }

      MultiMap<EntityReference<ModuleEntity>, PreIterator> splitDescriptions = new MultiMap<>();
      Map<VirtualFile, PreIterator> contentRootMap = new HashMap<>();

      ModuleManager moduleManager = ModuleManager.getInstance(project);
      //dirty hack to preserve old behaviour; todo[lene] try to get rid of it
      for (final Module module : moduleManager.getModules()) {
        final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

        for (ContentEntry contentEntry : moduleRootManager.getContentEntries()) {
          EntityReference<ContentRootEntity> entityReference = ((ContentEntryBridge)contentEntry).getEntity().createReference();
          for (ContentRootDescription description : allDescriptions) {
            if (entityReference.equals(description.reference)) {
              //content roots with same file should be taken only once, see com.intellij.openapi.roots.impl.RootIndex.RootInfo.contentRootOf
              if (!contentRootMap.containsKey(description.root)) {
                PreIterator preIterator = new PreIterator(description);
                splitDescriptions.putValue(description.moduleReference, preIterator);
                contentRootMap.put(description.root, preIterator);
              }
              break;
            }
          }
        }
      }


      for (ContentRootDescription description : allDescriptions) {
        //content roots with same file should be taken only once, see com.intellij.openapi.roots.impl.RootIndex.RootInfo.contentRootOf
        if (!contentRootMap.containsKey(description.root)) {
          PreIterator preIterator = new PreIterator(description);
          splitDescriptions.putValue(description.moduleReference, preIterator);
          contentRootMap.put(description.root, preIterator);
        }
      }

      for (Map.Entry<VirtualFile, PreIterator> entry : contentRootMap.entrySet()) {
        VirtualFile parent = entry.getKey().getParent();
        while (parent != null) {
          PreIterator parentDescription = contentRootMap.get(parent);
          if (parentDescription != null) {
            parentDescription.childContentRoots.add(entry.getKey());
            break;
          }
          parent = parent.getParent();
        }
      }

      ImmutableList.Builder<ContentRootOrigin> contentRootBuilder = new ImmutableList.Builder<>();
      for (Map.Entry<EntityReference<ModuleEntity>, Collection<PreIterator>> descriptionsEntry : splitDescriptions.entrySet()) {
        Collection<VirtualFile> excludedRoots = new ArrayList<>();
        for (PreIterator preIterator : descriptionsEntry.getValue()) {
          excludedRoots.addAll(preIterator.description.excludedPaths);
        }

        for (PreIterator preIterator : descriptionsEntry.getValue()) {
          Condition<VirtualFile> excludedCondition = null;
          List<String> patterns = preIterator.description().excludedPatterns;
          if (!patterns.isEmpty()) {
            FileTypeAssocTable<Boolean> table = new FileTypeAssocTable<>();
            for (String pattern : patterns) {
              table.addAssociation(FileNameMatcherFactory.getInstance().createMatcher(pattern), Boolean.TRUE);
            }
            excludedCondition = file -> {
              return table.findAssociatedFileType(file.getNameSequence()) != null;
            };
          }
          ModuleRootIterableOriginImpl origin = new ModuleRootIterableOriginImpl(preIterator.description.module,
                                                                                 Collections.singletonList(preIterator.root),
                                                                                 excludedRoots,
                                                                                 excludedCondition,
                                                                                 preIterator.childContentRoots);
          contentRootBuilder.add(new ContentRootOrigin(preIterator.description.reference, origin));
        }
      }

      ImmutableList.Builder<SourceRootOrigin> sourceRootBuilder = new ImmutableList.Builder<>();
      for (SourceRootDescription sourceRoot : sourceRoots) {
        VirtualFile root = sourceRoot.root;
        //See logic of com.intellij.openapi.roots.impl.ModuleFileIndexImpl.isInContent(VirtualFile, DirectoryInfo) &
        //com.intellij.openapi.roots.impl.RootIndex.RootInfo.findNearestContentRoot
        boolean shouldAdd = true;
        while (root != null) {
          PreIterator preIterator = contentRootMap.get(root);
          if (preIterator != null) {
            Collection<VirtualFile> excludedRoots = preIterator.description.excludedPaths;
            shouldAdd = preIterator.description().moduleReference.equals(sourceRoot.moduleReference) &&
                        !excludedRoots.contains(sourceRoot.root) && VfsUtilCore.isUnderFiles(sourceRoot.root, excludedRoots);
            break;
          }
          root = root.getParent();
        }
        if (!shouldAdd) {
          continue;
        }

        ModuleRootIterableOriginImpl origin = new ModuleRootIterableOriginImpl(sourceRoot.module,
                                                                               Collections.singletonList(sourceRoot.root),
                                                                               Collections.emptyList(),//todo[lene] inherit?
                                                                               null,//todo[lene] inherit?
                                                                               Collections.emptyList());
        sourceRootBuilder.add(new SourceRootOrigin(sourceRoot.reference, origin));
      }
      return new RootsOrigins(contentRootBuilder.build(), sourceRootBuilder.build());
    }

    @Nullable
    private ActualEntitiesSnapshot mergeRegeneratedEntities(
      @NotNull EntitiesToRegenerate entitiesToRegenerate,
      @NotNull Project project,
      @NotNull EntityStorage storage) {
      ImmutableList<EntityOrigins> merge = merge(entitiesToRegenerate.items, project, storage);
      List<ContentRootDescription> mergedContentRootDescriptions =
        mergeContentRootDescriptions(entitiesToRegenerate.contentRootItems, storage);
      List<SourceRootDescription> mergedSourceRootDescriptions =
        mergeSourceRootDescriptions(entitiesToRegenerate.sourceRootItems, storage);
      ImmutableList<EntityFiles> mergedExcludedRoots = mergeExcludedRoots(entitiesToRegenerate.items, project, storage);
      if (merge == null && mergedContentRootDescriptions == null && mergedSourceRootDescriptions == null && mergedExcludedRoots == null) {
        return null;
      }
      ImmutableList<ContentRootDescription> resultingContentRoots = mergedContentRootDescriptions == null
                                                                    ? contentRootDescriptions
                                                                    : ImmutableList.copyOf(mergedContentRootDescriptions);
      ImmutableList<SourceRootDescription> resultingSourceRoots = mergedSourceRootDescriptions == null
                                                                  ? sourceRootDescriptions
                                                                  : ImmutableList.copyOf(mergedSourceRootDescriptions);

      RootsOrigins newOrigins;
      if (mergedContentRootDescriptions == null && mergedSourceRootDescriptions == null) {
        newOrigins = rootOrigins;
      }
      else {
        newOrigins = buildRootsOrigins(project, resultingContentRoots, resultingSourceRoots);
      }
      return new ActualEntitiesSnapshot(merge == null ? entitiesOrigins : merge,
                                        newOrigins,
                                        resultingContentRoots,
                                        resultingSourceRoots,
                                        mergedExcludedRoots == null ? entitiesExcludedRoots : mergedExcludedRoots);
    }

    @Nullable
    private ImmutableList<EntityOrigins> merge(
      @NotNull LinkedHashMap<EntityReference<? extends WorkspaceEntity>, ChangeInformation<? extends WorkspaceEntity>> items,
      @NotNull Project project,
      @NotNull EntityStorage storage) {
      if (items.isEmpty()) return null;
      ImmutableList.Builder<EntityOrigins> copy = new ImmutableList.Builder<>();
      for (EntityOrigins entityOrigins : entitiesOrigins) {
        EntityReference<? extends WorkspaceEntity> entityReference = entityOrigins.reference;
        ChangeInformation<?> changeInformation = items.remove(entityReference);
        if (changeInformation == null) {
          copy.add(entityOrigins);
        }
        else if (changeInformation.action() == SetOrigin) {
          Collection<IndexableSetIterableOrigin> origins = changeInformation.generateOrigin(project, storage);
          if (!origins.isEmpty()) {
            copy.add(new EntityOrigins(changeInformation.reference, origins));
          }
        }
      }
      for (ChangeInformation<? extends WorkspaceEntity> changeInformation : items.values()) {
        if (changeInformation.action() == SetOrigin) {
          Collection<IndexableSetIterableOrigin> origins = changeInformation.generateOrigin(project, storage);
          if (!origins.isEmpty()) {
            copy.add(new EntityOrigins(changeInformation.reference, origins));
          }
        }
      }
      return copy.build();
    }

    @Nullable
    private List<ContentRootDescription> mergeContentRootDescriptions(
      @NotNull Map<EntityReference<ContentRootEntity>, ChangeInformation<ContentRootEntity>> items,
      @NotNull EntityStorage storage) {
      if (items.isEmpty()) return null;
      List<ContentRootDescription> descriptions = new ArrayList<>();
      for (ContentRootDescription initialDescription : contentRootDescriptions) {
        EntityReference<ContentRootEntity> entityReference = initialDescription.reference;
        ChangeInformation<ContentRootEntity> changeInformation = items.remove(entityReference);
        if (changeInformation == null) {
          descriptions.add(initialDescription);
        }
        else if (changeInformation.action() == SetOrigin) {
          ContainerUtil.addIfNotNull(descriptions, createContentRootDescription(changeInformation.entity, storage));
        }
      }
      for (ChangeInformation<ContentRootEntity> changeInformation : items.values()) {
        if (changeInformation.action() == SetOrigin) {
          ContainerUtil.addIfNotNull(descriptions, createContentRootDescription(changeInformation.entity, storage));
        }
      }
      return descriptions;
    }

    @Nullable
    private List<SourceRootDescription> mergeSourceRootDescriptions(
      @NotNull Map<EntityReference<SourceRootEntity>, ChangeInformation<SourceRootEntity>> items,
      @NotNull EntityStorage storage) {
      if (items.isEmpty()) return null;
      List<SourceRootDescription> descriptions = new ArrayList<>();
      for (SourceRootDescription initialDescription : sourceRootDescriptions) {
        EntityReference<SourceRootEntity> entityReference = initialDescription.reference;
        ChangeInformation<SourceRootEntity> changeInformation = items.remove(entityReference);
        if (changeInformation == null) {
          descriptions.add(initialDescription);
        }
        else if (changeInformation.action() == SetOrigin) {
          ContainerUtil.addIfNotNull(descriptions, createSourceRootDescription(changeInformation.entity, storage));
        }
      }
      for (ChangeInformation<SourceRootEntity> changeInformation : items.values()) {
        if (changeInformation.action() == SetOrigin) {
          ContainerUtil.addIfNotNull(descriptions, createSourceRootDescription(changeInformation.entity, storage));
        }
      }
      return descriptions;
    }

    @Nullable
    private ImmutableList<EntityFiles> mergeExcludedRoots(
      @NotNull LinkedHashMap<EntityReference<? extends WorkspaceEntity>, ChangeInformation<? extends WorkspaceEntity>> items,
      @NotNull Project project,
      @NotNull EntityStorage storage
    ) {
      if (items.isEmpty()) return null;
      ImmutableList.Builder<EntityFiles> copy = new ImmutableList.Builder<>();
      for (EntityFiles entityFiles : entitiesExcludedRoots) {
        EntityReference<? extends WorkspaceEntity> entityReference = entityFiles.reference;
        ChangeInformation<?> changeInformation = items.remove(entityReference);
        if (changeInformation == null) {
          copy.add(entityFiles);
        }
        else if (changeInformation.action() == SetOrigin) {
          Collection<VirtualFile> excludedRoots = changeInformation.generateExcludedRoots(project, storage);
          if (!excludedRoots.isEmpty()) {
            copy.add(new EntityFiles(changeInformation.reference, excludedRoots));
          }
        }
      }
      for (ChangeInformation<? extends WorkspaceEntity> changeInformation : items.values()) {
        if (changeInformation.action() == SetOrigin) {
          Collection<VirtualFile> excludedRoots = changeInformation.generateExcludedRoots(project, storage);
          if (!excludedRoots.isEmpty()) {
            copy.add(new EntityFiles(changeInformation.reference, excludedRoots));
          }
        }
      }
      return copy.build();
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
                                          @NotNull EntityReference<ModuleEntity> moduleReference,
                                          @NotNull Module module,
                                          @NotNull VirtualFile root,
                                          @NotNull List<VirtualFile> excludedPaths,
                                          @NotNull List<String> excludedPatterns) {
    }

    private record SourceRootDescription(@NotNull EntityReference<SourceRootEntity> reference,
                                         @NotNull EntityReference<ContentRootEntity> contentRootReference,
                                         @NotNull EntityReference<ModuleEntity> moduleReference,
                                         @NotNull Module module,
                                         @NotNull VirtualFile root,
                                         @Nullable VirtualFile contentRootRoot,
                                         @NotNull List<VirtualFile> excludedPaths,
                                         @NotNull List<String> excludedPatterns) {
    }

    private record ChangeInformation<E extends WorkspaceEntity>(@NotNull E entity,
                                                                @NotNull EntityReference<E> reference,
                                                                @NotNull IndexableEntityInducedChangesProvider.OriginAction action,
                                                                @NotNull IndexableEntityProvider.ExistingEx<E> provider) {
      @NotNull
      private Collection<IndexableSetIterableOrigin> generateOrigin(@NotNull Project project, @NotNull EntityStorage storage) {
        return provider.getExistingEntityIteratorOrigins(entity, storage, project);
      }

      @NotNull
      Collection<VirtualFile> generateExcludedRoots(@NotNull Project project, @NotNull EntityStorage storage) {
        return provider.getExcludedRoots(entity, storage, project);
      }
    }

    private static class EntitiesToRegenerate {
      private final @NotNull LinkedHashMap<EntityReference<? extends WorkspaceEntity>, ChangeInformation<? extends WorkspaceEntity>> items =
        new LinkedHashMap<>();
      private final @NotNull Map<EntityReference<ContentRootEntity>, ChangeInformation<ContentRootEntity>> contentRootItems =
        new HashMap<>();
      private final @NotNull Map<EntityReference<SourceRootEntity>, ChangeInformation<SourceRootEntity>> sourceRootItems =
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
        else if (entity.getEntityInterface().equals(SourceRootEntity.class)) {
          //noinspection unchecked
          sourceRootItems.put((EntityReference<SourceRootEntity>)reference,
                              new ChangeInformation<>((SourceRootEntity)entity,
                                                      (EntityReference<SourceRootEntity>)reference, action,
                                                      (IndexableEntityProvider.ExistingEx<SourceRootEntity>)provider));
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