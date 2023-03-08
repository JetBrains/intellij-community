// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import com.intellij.workspaceModel.core.fileIndex.*;
import com.intellij.workspaceModel.core.fileIndex.impl.LibraryRootFileIndexContributor;
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleContentOrSourceRootData;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl;
import com.intellij.workspaceModel.ide.VirtualFileUrls;
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryEntityUtils;
import com.intellij.workspaceModel.storage.EntityReference;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity;
import com.intellij.workspaceModel.storage.url.VirtualFileUrl;
import kotlin.jvm.functions.Function1;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

import static com.intellij.util.indexing.roots.LibraryIndexableFilesIteratorImpl.createIterator;

public class IndexingRootsCollectionUtil {

  /**
   * Usually it makes sense to deduplicate roots, for content root and source roots may share them.
   * But there may be too many of them, resulting in a freeze, especially for Rider or CLion who add each file as a root.
   */
  private static final int ROOTS_SIZE_OPTIMISING_LIMIT = 1000;

  private IndexingRootsCollectionUtil() { }

  public record LibraryRootsDescription(@NotNull LibraryEntity library,
                                        @NotNull List<? extends VirtualFile> classRoots,
                                        @NotNull List<? extends VirtualFile> sourceRoots) {
  }

  public record ModuleRootsDescription(@NotNull Module module,
                                       @NotNull List<? extends VirtualFile> roots) {
  }

  public record EntityContentRootsDescription(@NotNull EntityReference<?> entityReference,
                                              @NotNull Collection<? extends VirtualFile> roots) {
  }

  public record EntityRootsDescription(@NotNull EntityReference<?> entityReference,
                                       @NotNull Collection<? extends VirtualFile> roots,
                                       @NotNull Collection<? extends VirtualFile> sourceRoots) {
  }

  public record IndexingRootsDescriptions(@NotNull Collection<ModuleRootsDescription> moduleRoots,
                                          @NotNull Collection<EntityContentRootsDescription> contentEntityRoots,
                                          @NotNull Collection<LibraryRootsDescription> libraryRoots,
                                          @NotNull Collection<EntityRootsDescription> externalEntityRoots) {
    public IndexingRootsDescriptions() {
      this(new ArrayList<>(), new HashSet<>(), new HashSet<>(), new HashSet<>());
    }
  }

  public static class IndexingRootsCollectionSettings {
    public @Nullable Condition<? super WorkspaceFileIndexContributor<?>> retainCondition = null;
  }

  @NotNull
  public static IndexingRootsDescriptions collectRootsFromWorkspaceFileIndexContributors(@NotNull Project project,
                                                                                         @NotNull EntityStorage entityStorage,
                                                                                         @Nullable IndexingRootsCollectionSettings settings) {
    List<WorkspaceFileIndexContributor<?>> contributors =
      ((WorkspaceFileIndexImpl)WorkspaceFileIndex.getInstance(project)).getContributors();
    IndexingRootsDescriptions roots = new IndexingRootsDescriptions();
    RootsCollector registrar = new RootsCollector(settings);
    for (WorkspaceFileIndexContributor<?> contributor : contributors) {
      ProgressManager.checkCanceled();
      handleContributor(contributor, roots, registrar, entityStorage);
    }
    registrar.collectModuleAwareRoots(roots);
    return roots;
  }

  private static <E extends WorkspaceEntity> void handleContributor(@NotNull WorkspaceFileIndexContributor<E> contributor,
                                                                    @NotNull IndexingRootsCollectionUtil.IndexingRootsDescriptions roots,
                                                                    @NotNull IndexingRootsCollectionUtil.RootsCollector registrar,
                                                                    @NotNull EntityStorage entityStorage) {
    registrar.registerAndCollectNonModuleAwareRoots(roots, contributor, entityStorage.entities(contributor.getEntityClass()),
                                                    entityStorage);
  }

  public static void addIteratorsFromRootsDescriptions(@NotNull IndexingRootsDescriptions descriptions,
                                                       @NotNull List<IndexableFilesIterator> iterators,
                                                       @NotNull Set<IndexableSetOrigin> libraryOrigins,
                                                       @NotNull EntityStorage storage) {
    ArrayList<IndexableFilesIterator> initialIterators = new ArrayList<>();
    for (IndexingRootsCollectionUtil.ModuleRootsDescription moduleRootsDescription : descriptions.moduleRoots()) {
      initialIterators.add(new ModuleIndexableFilesIteratorImpl(moduleRootsDescription.module(), moduleRootsDescription.roots(), true));
    }
    for (IndexingRootsCollectionUtil.LibraryRootsDescription root : descriptions.libraryRoots()) {
      Library library = LibraryEntityUtils.findLibraryBridge(root.library, storage);
      if (library == null) {
        continue;
      }
      LibraryIndexableFilesIteratorImpl iterator = createIterator(library, root.classRoots(), root.sourceRoots());
      if (iterator != null && libraryOrigins.add(iterator.getOrigin())) {
        initialIterators.add(iterator);
      }
    }

    iterators.addAll(0, initialIterators);

    for (IndexingRootsCollectionUtil.EntityContentRootsDescription description : descriptions.contentEntityRoots()) {
      iterators.addAll(
        IndexableEntityProviderMethods.INSTANCE.createModuleUnawareContentEntityIterators(description.entityReference(),
                                                                                          description.roots()));
    }
    for (IndexingRootsCollectionUtil.EntityRootsDescription description : descriptions.externalEntityRoots()) {
      iterators.addAll(
        IndexableEntityProviderMethods.INSTANCE.createExternalEntityIterators(description.entityReference(), description.roots(),
                                                                              description.sourceRoots()));
    }
  }

  public static List<VirtualFile> optimizeRoots(@NotNull Collection<VirtualFile> roots) {
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

  private static <T> List<? extends T> toList(Collection<T> value) {
    if (value instanceof List<T>) return (List<? extends T>)value;
    if (value.isEmpty()) return Collections.emptyList();
    return new ArrayList<>(value);
  }

  public static class RootsCollector {
    private final MultiMap<Module, VirtualFile> myContents = MultiMap.create();
    private final IndexingRootsCollectionSettings mySettings;
    private final MultiMap<WorkspaceEntity, VirtualFile> myContentRoots = MultiMap.createSet();
    private final MultiMap<LibraryEntity, VirtualFile> myLibraryRoots = MultiMap.createSet();
    private final MultiMap<LibraryEntity, VirtualFile> myLibrarySourceRoots = MultiMap.createSet();
    private final MultiMap<WorkspaceEntity, VirtualFile> myExternalRoots = MultiMap.createSet();
    private final MultiMap<WorkspaceEntity, VirtualFile> myExternalSourceRoots = MultiMap.createSet();

    @Nullable
    private WorkspaceFileIndexContributor<?> myCurrentContributor;
    private final MyWorkspaceFileSetRegistrar myRegistrar = new MyWorkspaceFileSetRegistrar();

    public RootsCollector(@Nullable IndexingRootsCollectionSettings settings) {
      mySettings = ObjectUtils.notNull(settings, new IndexingRootsCollectionSettings());
    }

    public void clearNonModuleAwareMaps() {
      myContentRoots.clear();
      myLibraryRoots.clear();
      myLibrarySourceRoots.clear();
      myExternalRoots.clear();
      myExternalSourceRoots.clear();
    }

    /**
     * Module content roots are collected and retained inside
     */
    public <E extends WorkspaceEntity> void registerAndCollectNonModuleAwareRoots(@NotNull IndexingRootsDescriptions roots,
                                                                                  @NotNull WorkspaceFileIndexContributor<E> contributor,
                                                                                  @NotNull Sequence<E> entities,
                                                                                  @NotNull EntityStorage entityStorage) {
      if (shouldIgnore(contributor)) return;
      clearNonModuleAwareMaps();
      myCurrentContributor = contributor;
      for (E entity : SequencesKt.asIterable(entities)) {
        contributor.registerFileSets(entity, myRegistrar, entityStorage);
      }
      myCurrentContributor = null;
      doCollectNonModuleAwareRoots(roots);
      clearNonModuleAwareMaps();
    }

    private void doCollectNonModuleAwareRoots(@NotNull IndexingRootsDescriptions roots) {
      for (Map.Entry<WorkspaceEntity, Collection<VirtualFile>> entry : myContentRoots.entrySet()) {
        roots.contentEntityRoots.add(new EntityContentRootsDescription(entry.getKey().createReference(), entry.getValue()));
      }

      for (Map.Entry<LibraryEntity, Collection<VirtualFile>> entry : myLibraryRoots.entrySet()) {
        LibraryEntity entity = entry.getKey();
        Collection<VirtualFile> sourceRoots = ObjectUtils.notNull(myLibrarySourceRoots.remove(entity), Collections.emptyList());
        roots.libraryRoots.add(new LibraryRootsDescription(entity, toList(entry.getValue()), toList(sourceRoots)));
      }
      for (Map.Entry<LibraryEntity, Collection<VirtualFile>> entry : myLibrarySourceRoots.entrySet()) {
        roots.libraryRoots.add(new LibraryRootsDescription(entry.getKey(), Collections.emptyList(), toList(entry.getValue())));
      }

      for (Map.Entry<WorkspaceEntity, Collection<VirtualFile>> entry : myExternalRoots.entrySet()) {
        WorkspaceEntity entity = entry.getKey();
        Collection<VirtualFile> sourceRoots = myExternalSourceRoots.remove(entity);
        sourceRoots = ObjectUtils.notNull(sourceRoots, Collections.emptyList());
        roots.externalEntityRoots.add(new EntityRootsDescription(entity.createReference(), entry.getValue(), sourceRoots));
      }
      for (Map.Entry<WorkspaceEntity, Collection<VirtualFile>> entry : myExternalSourceRoots.entrySet()) {
        WorkspaceEntity entity = entry.getKey();
        roots.externalEntityRoots.add(new EntityRootsDescription(entity.createReference(), Collections.emptyList(), entry.getValue()));
      }
    }

    private <E extends WorkspaceEntity> boolean shouldIgnore(@NotNull WorkspaceFileIndexContributor<E> contributor) {
      if (contributor.getStorageKind() != EntityStorageKind.MAIN) {
        return true;
      }
      if (mySettings.retainCondition != null && !mySettings.retainCondition.value(contributor)) {
        return true;
      }
      return false;
    }

    private void collectModuleAwareRoots(@NotNull IndexingRootsDescriptions roots) {
      for (Map.Entry<Module, Collection<VirtualFile>> entry : myContents.entrySet()) {
        if (!entry.getValue().isEmpty()) {
          roots.moduleRoots.add(new ModuleRootsDescription(entry.getKey(), optimizeRoots(entry.getValue())));
          ProgressManager.checkCanceled();
        }
      }
      myContents.clear();
    }

    private class MyWorkspaceFileSetRegistrar implements WorkspaceFileSetRegistrar {

      @Override
      public void registerFileSet(@NotNull VirtualFileUrl root,
                                  @NotNull WorkspaceFileKind kind,
                                  @NotNull WorkspaceEntity entity,
                                  @Nullable WorkspaceFileSetData customData) {
        VirtualFile file = VirtualFileUrls.getVirtualFile(root);
        if (file != null) {
          doRegisterFileSet(file, kind, entity, customData);
        }
      }

      @Override
      public void registerFileSet(@NotNull VirtualFile root,
                                  @NotNull WorkspaceFileKind kind,
                                  @NotNull WorkspaceEntity entity,
                                  @Nullable WorkspaceFileSetData customData) {
        doRegisterFileSet(root, kind, entity, customData);
      }

      private void doRegisterFileSet(@NotNull VirtualFile root,
                                     @NotNull WorkspaceFileKind kind,
                                     @NotNull WorkspaceEntity entity,
                                     @Nullable WorkspaceFileSetData customData) {
        if (customData instanceof ModuleContentOrSourceRootData) {
          myContents.putValue(((ModuleContentOrSourceRootData)customData).getModule(), root);
        }
        else if (kind.isContent()) {
          myContentRoots.putValue(entity, root);
        }
        else if (kind == WorkspaceFileKind.EXTERNAL) {
          if (myCurrentContributor instanceof LibraryRootFileIndexContributor) {
            myLibraryRoots.putValue((LibraryEntity)entity, root);
          }
          else {
            myExternalRoots.putValue(entity, root);
          }
        }
        else {
          if (myCurrentContributor instanceof LibraryRootFileIndexContributor) {
            myLibrarySourceRoots.putValue((LibraryEntity)entity, root);
          }
          else {
            myExternalSourceRoots.putValue(entity, root);
          }
        }
      }

      @Override
      public void registerExcludedRoot(@NotNull VirtualFileUrl excludedRoot, @NotNull WorkspaceEntity entity) {
      }

      @Override
      public void registerExcludedRoot(@NotNull VirtualFileUrl excludedRoot,
                                       @NotNull WorkspaceFileKind excludedFrom,
                                       @NotNull WorkspaceEntity entity) {
      }

      @Override
      public void registerExcludedRoot(@NotNull VirtualFile excludedRoot,
                                       @NotNull WorkspaceFileKind excludedFrom,
                                       @NotNull WorkspaceEntity entity) {
      }

      @Override
      public void registerExclusionPatterns(@NotNull VirtualFileUrl root, @NotNull List<String> patterns, @NotNull WorkspaceEntity entity) {
      }

      @Override
      public void registerExclusionCondition(@NotNull VirtualFile root,
                                             @NotNull Function1<? super VirtualFile, Boolean> condition,
                                             @NotNull WorkspaceEntity entity) {
      }

      @Override
      public void registerExclusionCondition(@NotNull VirtualFileUrl root,
                                             @NotNull Function1<? super VirtualFile, Boolean> condition,
                                             @NotNull WorkspaceEntity entity) {
      }
    }
  }
}