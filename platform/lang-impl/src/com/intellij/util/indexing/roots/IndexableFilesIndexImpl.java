// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.AdditionalIndexableFileSet;
import com.intellij.util.indexing.IndexableFilesIndex;
import com.intellij.util.indexing.IndexableSetContributor;
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import com.intellij.workspaceModel.core.fileIndex.*;
import com.intellij.workspaceModel.core.fileIndex.impl.*;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.ide.impl.UtilsKt;
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleEntityUtils;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
import com.intellij.workspaceModel.storage.url.VirtualFileUrl;
import kotlin.jvm.functions.Function1;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

import static com.intellij.util.indexing.roots.IndexableEntityProviderMethods.INSTANCE;
import static com.intellij.util.indexing.roots.LibraryIndexableFilesIteratorImpl.Companion;
import static com.intellij.util.indexing.roots.LibraryIndexableFilesIteratorImpl.createIterator;

@ApiStatus.Experimental
@ApiStatus.Internal
public class IndexableFilesIndexImpl implements IndexableFilesIndex {
  /**
   * Usually it makes sense to deduplicate roots, for content root and source roots may share them.
   * But there may be too many of them, resulting in a freeze, especially for Rider or CLion who add each file as a root.
   */
  private static final int ROOTS_SIZE_OPTIMISING_LIMIT = 1000;
  @NotNull
  private final Project project;
  private final AdditionalIndexableFileSet filesFromIndexableSetContributors;

  @NotNull
  public static IndexableFilesIndexImpl getInstanceImpl(@NotNull Project project) {
    return (IndexableFilesIndexImpl)IndexableFilesIndex.getInstance(project);
  }


  public IndexableFilesIndexImpl(@NotNull Project project) {
    this.project = project;
    filesFromIndexableSetContributors = new AdditionalIndexableFileSet(project);
  }

  @Override
  public boolean shouldBeIndexed(@NotNull VirtualFile file) {
    if (WorkspaceFileIndex.getInstance(project).isInWorkspace(file)) return true;
    return filesFromIndexableSetContributors.isInSet(file);
  }

  @Override
  public @NotNull List<IndexableFilesIterator> getIndexingIterators() {
    return ReadAction.nonBlocking(this::doGetIndexingIterators).expireWith(project).executeSynchronously();
  }

  @NotNull
  private List<IndexableFilesIterator> doGetIndexingIterators() {
    EntityStorage entityStorage = WorkspaceModel.getInstance(project).getEntityStorage().getCurrent();
    List<WorkspaceFileIndexContributor<?>> contributors =
      ((WorkspaceFileIndexImpl)WorkspaceFileIndex.getInstance(project)).getContributors();
    List<IndexableFilesIterator> iterators = new ArrayList<>();

    MyRegistrar registrar = new MyRegistrar();
    for (WorkspaceFileIndexContributor<?> contributor : contributors) {
      if (contributor.getStorageKind() != EntityStorageKind.MAIN) {
        continue;
      }
      ProgressManager.checkCanceled();
      handleContributor(entityStorage, registrar, contributor, iterators);
    }

    List<Sdk> sdks = new ArrayList<>();
    ModuleDependencyIndex moduleDependencyIndex = ModuleDependencyIndex.getInstance(project);
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (moduleDependencyIndex.hasDependencyOn(sdk)) {
        sdks.add(sdk);
      }
    }
    if (sdks.isEmpty()) {
      Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
      if (projectSdk != null) {
        sdks.add(projectSdk);
      }
    }
    for (Sdk sdk : sdks) {
      ProgressManager.checkCanceled();
      iterators.addAll(INSTANCE.createIterators(sdk));
    }

    LibraryTablesRegistrar tablesRegistrar = LibraryTablesRegistrar.getInstance();
    Sequence<LibraryTable> libs = SequencesKt.asSequence(tablesRegistrar.getCustomLibraryTables().iterator());
    libs = SequencesKt.plus(libs, tablesRegistrar.getLibraryTable());
    for (LibraryTable libraryTable : SequencesKt.asIterable(libs)) {
      for (Library library : libraryTable.getLibraries()) {
        ProgressManager.checkCanceled();
        if (moduleDependencyIndex.hasDependencyOn(library)) {
          iterators.addAll(Companion.createIteratorList(library));
        }
      }
    }

    ProgressManager.checkCanceled();
    List<IndexableFilesIterator> contentIterators = new ArrayList<>(registrar.myContents.size());
    for (Map.Entry<Module, Collection<VirtualFile>> entry : registrar.myContents.entrySet()) {
      if (!entry.getValue().isEmpty()) {
        contentIterators.add(new ModuleIndexableFilesIteratorImpl(entry.getKey(), optimizeRoots(entry.getValue()), true));
      }
    }
    iterators.addAll(0, contentIterators);

    boolean addedFromDependenciesIndexedStatusService = false;
    if (DependenciesIndexedStatusService.shouldBeUsed()) {
      DependenciesIndexedStatusService cacheService = DependenciesIndexedStatusService.getInstance(project);
      if (cacheService.shouldSaveStatus()) {
        addedFromDependenciesIndexedStatusService = true;
        ProgressManager.checkCanceled();
        iterators.addAll(cacheService.saveIndexableSetsAndInstantiateIterators());
        ProgressManager.checkCanceled();
        iterators.addAll(cacheService.saveLibsAndInstantiateLibraryIterators());
        cacheService.saveExcludePolicies();
      }
    }

    if (!addedFromDependenciesIndexedStatusService) {
      for (AdditionalLibraryRootsProvider provider : AdditionalLibraryRootsProvider.EP_NAME.getExtensionList()) {
        for (SyntheticLibrary library : provider.getAdditionalProjectLibraries(project)) {
          iterators.add(new SyntheticLibraryIndexableFilesIteratorImpl(library));
        }
      }

      for (IndexableSetContributor contributor : IndexableSetContributor.EP_NAME.getExtensionList()) {
        iterators.add(new IndexableSetContributorFilesIterator(contributor, project));
        iterators.add(new IndexableSetContributorFilesIterator(contributor));
      }
    }
    return iterators;
  }

  private static <E extends WorkspaceEntity> void handleContributor(EntityStorage entityStorage,
                                                                    MyRegistrar registrar,
                                                                    WorkspaceFileIndexContributor<E> contributor,
                                                                    List<IndexableFilesIterator> iterators) {
    registrar.initEntityMaps();
    Sequence<E> entities = entityStorage.entities(contributor.getEntityClass());
    for (E entity : SequencesKt.asIterable(entities)) {
      contributor.registerFileSets(entity, registrar, entityStorage);
    }
    if (contributor instanceof LibraryRootFileIndexContributor) {
      Set<IndexableSetOrigin> origins = new HashSet<>();
      for (Map.Entry<WorkspaceEntity, Collection<VirtualFile>> entry : registrar.myExternalRoots.entrySet()) {
        WorkspaceEntity entity = entry.getKey();
        Collection<VirtualFile> sourceRoots = ObjectUtils.notNull(registrar.myExternalSourceRoots.remove(entity), Collections.emptyList());
        LibraryIndexableFilesIteratorImpl iterator = createIterator(entity, toList(entry.getValue()), toList(sourceRoots));
        if (origins.add(iterator.getOrigin())) {
          iterators.add(iterator);
        }
      }
      for (Map.Entry<WorkspaceEntity, Collection<VirtualFile>> entry : registrar.myExternalSourceRoots.entrySet()) {
        LibraryIndexableFilesIteratorImpl iterator = createIterator(entry.getKey(), Collections.emptyList(), toList(entry.getValue()));
        if (origins.add(iterator.getOrigin())) {
          iterators.add(iterator);
        }
      }
    }
    else {
      for (Map.Entry<WorkspaceEntity, Collection<VirtualFile>> entry : registrar.myContentRoots.entrySet()) {
        iterators.addAll(INSTANCE.createModuleUnawareContentEntityIterators(entry.getKey().createReference(), entry.getValue()));
      }
      for (Map.Entry<WorkspaceEntity, Collection<VirtualFile>> entry : registrar.myExternalRoots.entrySet()) {
        WorkspaceEntity entity = entry.getKey();
        Collection<VirtualFile> sourceRoots = registrar.myExternalSourceRoots.remove(entity);
        sourceRoots = ObjectUtils.notNull(sourceRoots, Collections.emptyList());
        iterators.addAll(INSTANCE.createExternalEntityIterators(entity.createReference(), entry.getValue(), sourceRoots));
      }
      for (Map.Entry<WorkspaceEntity, Collection<VirtualFile>> entry : registrar.myExternalSourceRoots.entrySet()) {
        WorkspaceEntity entity = entry.getKey();
        iterators.addAll(INSTANCE.createExternalEntityIterators(entity.createReference(), Collections.emptyList(), entry.getValue()));
      }
    }
    registrar.cleanEntityMaps();
  }

  private static <T> List<? extends T> toList(Collection<T> value) {
    if (value instanceof List<T>) return (List<? extends T>)value;
    if (value.isEmpty()) return Collections.emptyList();
    return new ArrayList<>(value);
  }

  @Override
  public @NotNull Collection<IndexableFilesIterator> getModuleIndexingIterators(@NotNull ModuleEntity entity,
                                                                                @NotNull EntityStorage entityStorage) {
    ModuleBridge module = ModuleEntityUtils.findModule(entity, entityStorage);
    if (module == null) {
      return Collections.emptyList();
    }
    List<VirtualFile> roots = getModuleRootsToIndex(module);
    if (roots.isEmpty()) return Collections.emptyList();
    return Collections.singletonList(new ModuleIndexableFilesIteratorImpl(module, roots, true));
  }

  @NotNull
  private List<VirtualFile> getModuleRootsToIndex(@NotNull Module module) {
    List<VirtualFile> roots = ReadAction.nonBlocking(() -> {
      if (project.isDisposed()) return null;
      WorkspaceFileIndexEx index = (WorkspaceFileIndexEx)WorkspaceFileIndex.getInstance(project);
      List<VirtualFile> files = new SmartList<>();
      index.visitFileSets(new WorkspaceFileSetVisitor() {
        @Override
        public void visitIncludedRoot(@NotNull WorkspaceFileSet fileSet) {
          if (!(fileSet instanceof WorkspaceFileSetWithCustomData<?>)) return;
          ModuleContentOrSourceRootData data =
            ObjectUtils.tryCast(((WorkspaceFileSetWithCustomData<?>)fileSet).getData(), ModuleContentOrSourceRootData.class);
          if (data != null && data.getModule().equals(module)) {
            files.add(fileSet.getRoot());
          }
        }
      });
      return files;
    }).executeSynchronously();
    return optimizeRoots(roots);
  }

  private static List<VirtualFile> optimizeRoots(@NotNull Collection<VirtualFile> roots) {
    int size = roots.size();
    if (size == 0) {
      return Collections.emptyList();
    }
    else if (size == 1) {
      return new SmartList<>(roots.iterator().next());
    }
    else if (size > ROOTS_SIZE_OPTIMISING_LIMIT) {
      return new ArrayList<>(roots);
    }
    else {
      List<VirtualFile> filteredList = new ArrayList<>();
      Consumer<VirtualFile> consumer = new Consumer<>() {
        private String previousPath = null;

        @Override
        public void accept(VirtualFile file) {
          String path = file.getPath();
          if (previousPath == null || !FileUtil.startsWith(path, previousPath)) {
            filteredList.add(file);
            previousPath = path;
          }
        }
      };
      roots.stream().sorted((o1, o2) -> StringUtil.compare(o1.getPath(), o2.getPath(), false)).forEachOrdered(consumer);
      return filteredList;
    }
  }

  private static class MyRegistrar implements WorkspaceFileSetRegistrar {
    final MultiMap<Module, VirtualFile> myContents = MultiMap.create();
    MultiMap<WorkspaceEntity, VirtualFile> myContentRoots;
    MultiMap<WorkspaceEntity, VirtualFile> myExternalRoots;
    MultiMap<WorkspaceEntity, VirtualFile> myExternalSourceRoots;

    @Override
    public void registerFileSet(@NotNull VirtualFileUrl root,
                                @NotNull WorkspaceFileKind kind,
                                @NotNull WorkspaceEntity entity,
                                @Nullable WorkspaceFileSetData customData) {
      VirtualFile file = UtilsKt.getVirtualFile(root);
      if (file != null) {
        registerFileSet(file, kind, entity, customData);
      }
    }

    @Override
    public void registerFileSet(@NotNull VirtualFile root,
                                @NotNull WorkspaceFileKind kind,
                                @NotNull WorkspaceEntity entity,
                                @Nullable WorkspaceFileSetData customData) {
      if (customData instanceof ModuleContentOrSourceRootData) {
        myContents.putValue(((ModuleContentOrSourceRootData)customData).getModule(), root);
      }
      else if (kind == WorkspaceFileKind.CONTENT || kind == WorkspaceFileKind.TEST_CONTENT) {
        myContentRoots.putValue(entity, root);
      }
      else if (kind == WorkspaceFileKind.EXTERNAL) {
        myExternalRoots.putValue(entity, root);
      }
      else {
        myExternalSourceRoots.putValue(entity, root);
      }
    }

    @Override
    public void registerExcludedRoot(@NotNull VirtualFileUrl excludedRoot, @NotNull WorkspaceEntity entity) {
    }

    @Override
    public void registerExcludedRoot(@NotNull VirtualFile excludedRoot,
                                     @NotNull WorkspaceFileKind excludedFrom,
                                     @NotNull WorkspaceEntity entity) {
    }

    @Override
    public void registerExclusionPatterns(@NotNull VirtualFileUrl root,
                                          @NotNull List<String> patterns,
                                          @NotNull WorkspaceEntity entity) {
    }

    @Override
    public void registerExclusionCondition(@NotNull VirtualFile root,
                                           @NotNull Function1<? super VirtualFile, Boolean> condition,
                                           @NotNull WorkspaceEntity entity) {
    }

    public void initEntityMaps() {
      myContentRoots = MultiMap.createSet();
      myExternalRoots = MultiMap.createSet();
      myExternalSourceRoots = MultiMap.createSet();
    }

    public void cleanEntityMaps() {
      myContentRoots = null;
      myExternalRoots = null;
      myExternalSourceRoots = null;
    }
  }
}