// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import com.intellij.workspaceModel.core.fileIndex.*;
import com.intellij.workspaceModel.core.fileIndex.impl.LibraryRootFileIndexContributor;
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleContentOrSourceRootData;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl;
import com.intellij.workspaceModel.ide.impl.UtilsKt;
import com.intellij.workspaceModel.storage.EntityReference;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.url.VirtualFileUrl;
import kotlin.jvm.functions.Function1;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public class IndexingRootsCollectionUtil {

  /**
   * Usually it makes sense to deduplicate roots, for content root and source roots may share them.
   * But there may be too many of them, resulting in a freeze, especially for Rider or CLion who add each file as a root.
   */
  private static final int ROOTS_SIZE_OPTIMISING_LIMIT = 1000;

  private IndexingRootsCollectionUtil() { }

  public record LibraryRootsDescription(@NotNull WorkspaceEntity library,
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
    public boolean collectModuleAwareContent = true;
    public @Nullable Condition<ModuleContentOrSourceRootData> retainModuleContentCondition = null;
    public boolean collectModuleUnawareContent = true;
    public boolean collectExternalEntities = true;
    public boolean collectExternalSourceEntities = true;
  }

  @NotNull
  public static IndexingRootsDescriptions collectRootsFromWorkspaceFileIndexContributors(@NotNull Project project,
                                                                                         @NotNull EntityStorage entityStorage,
                                                                                         @Nullable IndexingRootsCollectionSettings settings) {
    List<WorkspaceFileIndexContributor<?>> contributors =
      ((WorkspaceFileIndexImpl)WorkspaceFileIndex.getInstance(project)).getContributors();

    settings = ObjectUtils.notNull(settings, new IndexingRootsCollectionSettings());
    IndexingRootsDescriptions roots = new IndexingRootsDescriptions();
    MyRegistrar registrar = new MyRegistrar(settings);
    for (WorkspaceFileIndexContributor<?> contributor : contributors) {
      if (contributor.getStorageKind() != EntityStorageKind.MAIN) {
        continue;
      }
      ProgressManager.checkCanceled();
      if (settings.retainCondition != null && !settings.retainCondition.value(contributor)) {
        continue;
      }
      handleContributor(contributor, roots, registrar, entityStorage);
    }

    for (Map.Entry<Module, Collection<VirtualFile>> entry : registrar.myContents.entrySet()) {
      if (!entry.getValue().isEmpty()) {
        roots.moduleRoots.add(new ModuleRootsDescription(entry.getKey(), optimizeRoots(entry.getValue())));
        ProgressManager.checkCanceled();
      }
    }

    return roots;
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

  private static <E extends WorkspaceEntity> void handleContributor(@NotNull WorkspaceFileIndexContributor<E> contributor,
                                                                    @NotNull IndexingRootsCollectionUtil.IndexingRootsDescriptions roots,
                                                                    @NotNull MyRegistrar registrar,
                                                                    @NotNull EntityStorage entityStorage) {
    registrar.initEntityMaps();
    Sequence<E> entities = entityStorage.entities(contributor.getEntityClass());
    for (E entity : SequencesKt.asIterable(entities)) {
      contributor.registerFileSets(entity, registrar, entityStorage);
    }
    if (contributor instanceof LibraryRootFileIndexContributor) {
      for (Map.Entry<WorkspaceEntity, Collection<VirtualFile>> entry : registrar.myExternalRoots.entrySet()) {
        WorkspaceEntity entity = entry.getKey();
        Collection<VirtualFile> sourceRoots = ObjectUtils.notNull(registrar.myExternalSourceRoots.remove(entity), Collections.emptyList());
        roots.libraryRoots.add(new LibraryRootsDescription(entity, toList(entry.getValue()), toList(sourceRoots)));
      }
      for (Map.Entry<WorkspaceEntity, Collection<VirtualFile>> entry : registrar.myExternalSourceRoots.entrySet()) {
        roots.libraryRoots.add(new LibraryRootsDescription(entry.getKey(), Collections.emptyList(), toList(entry.getValue())));
      }
    }
    else {
      for (Map.Entry<WorkspaceEntity, Collection<VirtualFile>> entry : registrar.myContentRoots.entrySet()) {
        roots.contentEntityRoots.add(new EntityContentRootsDescription(entry.getKey().createReference(), entry.getValue()));
      }
      for (Map.Entry<WorkspaceEntity, Collection<VirtualFile>> entry : registrar.myExternalRoots.entrySet()) {
        WorkspaceEntity entity = entry.getKey();
        Collection<VirtualFile> sourceRoots = registrar.myExternalSourceRoots.remove(entity);
        sourceRoots = ObjectUtils.notNull(sourceRoots, Collections.emptyList());
        roots.externalEntityRoots.add(new EntityRootsDescription(entity.createReference(), entry.getValue(), sourceRoots));
      }
      for (Map.Entry<WorkspaceEntity, Collection<VirtualFile>> entry : registrar.myExternalSourceRoots.entrySet()) {
        WorkspaceEntity entity = entry.getKey();
        roots.externalEntityRoots.add(new EntityRootsDescription(entity.createReference(), Collections.emptyList(), entry.getValue()));
      }
    }
    registrar.cleanEntityMaps();
  }

  private static <T> List<? extends T> toList(Collection<T> value) {
    if (value instanceof List<T>) return (List<? extends T>)value;
    if (value.isEmpty()) return Collections.emptyList();
    return new ArrayList<>(value);
  }

  private static class MyRegistrar implements WorkspaceFileSetRegistrar {
    final MultiMap<Module, VirtualFile> myContents = MultiMap.create();
    private final IndexingRootsCollectionSettings mySettings;
    private MultiMap<WorkspaceEntity, VirtualFile> myContentRoots;
    private MultiMap<WorkspaceEntity, VirtualFile> myExternalRoots;
    private MultiMap<WorkspaceEntity, VirtualFile> myExternalSourceRoots;

    private MyRegistrar(IndexingRootsCollectionSettings settings) {
      mySettings = settings;
    }

    @Override
    public void registerFileSet(@NotNull VirtualFileUrl root,
                                @NotNull WorkspaceFileKind kind,
                                @NotNull WorkspaceEntity entity,
                                @Nullable WorkspaceFileSetData customData) {
      if (shouldIgnore(kind, customData)) return;
      VirtualFile file = UtilsKt.getVirtualFile(root);
      if (file != null) {
        registerFileSet(file, kind, entity, customData);
      }
    }

    private boolean shouldIgnore(@NotNull WorkspaceFileKind kind, @Nullable WorkspaceFileSetData data) {
      if (data instanceof ModuleContentOrSourceRootData) {
        if (!mySettings.collectModuleAwareContent) return true;

        if (mySettings.retainModuleContentCondition != null &&
            !mySettings.retainModuleContentCondition.value((ModuleContentOrSourceRootData)data)) {
          return true;
        }
      }
      return switch (kind) {
        case CONTENT, TEST_CONTENT -> !mySettings.collectModuleUnawareContent;
        case EXTERNAL -> !mySettings.collectExternalEntities;
        case EXTERNAL_SOURCE -> !mySettings.collectExternalSourceEntities;
      };
    }

    @Override
    public void registerFileSet(@NotNull VirtualFile root,
                                @NotNull WorkspaceFileKind kind,
                                @NotNull WorkspaceEntity entity,
                                @Nullable WorkspaceFileSetData customData) {
      if (shouldIgnore(kind, customData)) return;
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
