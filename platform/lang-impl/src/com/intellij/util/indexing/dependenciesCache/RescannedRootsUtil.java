// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependenciesCache;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.impl.CustomEntityProjectModelInfoProvider;
import com.intellij.openapi.roots.impl.CustomEntityProjectModelInfoProvider.LibraryRoots;
import com.intellij.openapi.roots.impl.DirectoryInfo;
import com.intellij.openapi.roots.impl.ProjectFileIndexImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.roots.IndexableEntityProvider;
import com.intellij.util.indexing.roots.IndexableEntityProvider.IndexableIteratorBuilder;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.util.indexing.roots.builders.SyntheticLibraryIteratorBuilder;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryEntityUtils;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryId;
import com.intellij.workspaceModel.storage.url.VirtualFileUrl;
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@ApiStatus.Internal
class RescannedRootsUtil {
  static Collection<? extends IndexableIteratorBuilder> getUnexcludedRootsIteratorBuilders(@NotNull Project project,
                                                                                           @NotNull List<? extends SyntheticLibraryDescriptor> libraryDescriptorsBefore,
                                                                                           @NotNull List<? extends ExcludePolicyDescriptor> excludedDescriptorsBefore,
                                                                                           @NotNull List<? extends SyntheticLibraryDescriptor> librariesDescriptorsAfter) {
    Set<VirtualFile> excludedRoots = new HashSet<>();
    for (SyntheticLibraryDescriptor value : libraryDescriptorsBefore) {
      excludedRoots.addAll(value.excludedRoots);
    }
    for (ExcludePolicyDescriptor value : excludedDescriptorsBefore) {
      excludedRoots.addAll(value.getExcludedRoots());
      excludedRoots.addAll(value.excludedFromSdkRoots);
    }

    if (excludedRoots.isEmpty()) return Collections.emptyList();

    ProjectFileIndex index = ProjectFileIndex.getInstance(project);
    if (!(index instanceof ProjectFileIndexImpl)) return Collections.emptyList();
    ProjectFileIndexImpl fileIndex = (ProjectFileIndexImpl)index;
    ArrayList<IndexableIteratorBuilder> result = new ArrayList<>();
    Iterator<VirtualFile> iterator = excludedRoots.iterator();
    VirtualFileUrlManager urlManager = project.getService(VirtualFileUrlManager.class);

    while (iterator.hasNext()) {
      VirtualFile excluded = iterator.next();
      DirectoryInfo info = fileIndex.getInfoForFileOrDirectory(excluded);
      if (!info.isInProject(excluded)) {
        iterator.remove();
        continue;
      }
      Module module = info.getModule();
      if (module != null) {
        VirtualFileUrl url = urlManager.fromUrl(excluded.getUrl());
        result.addAll(IndexableIteratorBuilders.INSTANCE.forModuleRoots(((ModuleBridge)module).getModuleEntityId(),
                                                                        Collections.singletonList(url)));
        iterator.remove();
        continue;
      }

      boolean found = false;
      for (OrderEntry entry : index.getOrderEntriesForFile(excluded)) {//todo[lene] switch to IndexableFilesIndex
        if (entry instanceof JdkOrderEntry) {
          Sdk sdk = ((JdkOrderEntry)entry).getJdk();
          if (sdk != null) {
            found = true;
            result.addAll(IndexableIteratorBuilders.INSTANCE.forSdk(sdk, excluded));
          }
        }
        else if (entry instanceof LibraryOrderEntry) {
          Library library = ((LibraryOrderEntry)entry).getLibrary();
          if (library != null) {
            found = true;
            LibraryId libraryId = LibraryEntityUtils.findLibraryId(library);
            result.addAll(IndexableIteratorBuilders.INSTANCE.forLibraryEntity(libraryId, false, excluded));
          }
        }
      }

      SyntheticLibraryIteratorBuilder builder = createSyntheticLibraryIterator(librariesDescriptorsAfter, excluded);
      if (builder != null) {
        result.add(builder);
        iterator.remove();
        continue;
      }

      if (found) {
        iterator.remove();
      }
    }

    if (excludedRoots.isEmpty()) {
      return result;
    }

    EntityStorage current = WorkspaceModel.getInstance(project).getEntityStorage().getCurrent();
    for (CustomEntityProjectModelInfoProvider<?> provider : CustomEntityProjectModelInfoProvider.EP.getExtensionList()) {
      for (LibraryRoots<? extends WorkspaceEntity> roots : getRoots(provider, current)) {
        Iterator<VirtualFile> rootsIterator = excludedRoots.iterator();
        while (rootsIterator.hasNext()) {
          VirtualFile next = rootsIterator.next();
          if (VfsUtilCore.isUnderFiles(next, roots.sources) || VfsUtilCore.isUnderFiles(next, roots.classes)) {
            if (!VfsUtilCore.isUnderFiles(next, roots.excluded)) {
              Collection<? extends IndexableIteratorBuilder> builders = createCustomEntityBuilder(roots.generativeEntity, project);
              if (!builders.isEmpty()) {
                result.addAll(builders);
                rootsIterator.remove();
              }
            }
          }
        }
        if (excludedRoots.isEmpty()) {
          break;
        }
      }
    }

    if (!excludedRoots.isEmpty()) {
      throw new IllegalStateException("Roots were not found: " + StringUtil.join(excludedRoots, "\n"));
    }
    return result;
  }

  @NotNull
  private static <E extends WorkspaceEntity> Collection<? extends IndexableIteratorBuilder> createCustomEntityBuilder(E entity,
                                                                                                                      Project project) {
    for (IndexableEntityProvider<? extends WorkspaceEntity> provider : IndexableEntityProvider.EP_NAME.getExtensionList()) {
      if (provider instanceof IndexableEntityProvider.Existing && provider.getEntityClass().equals(entity.getEntityInterface())) {
        //noinspection unchecked
        return ((IndexableEntityProvider.Existing<E>)provider).getExistingEntityIteratorBuilder(entity, project);
      }
    }
    return Collections.emptyList();
  }

  private static <E extends WorkspaceEntity> Iterable<LibraryRoots<E>> getRoots(CustomEntityProjectModelInfoProvider<E> provider,
                                                                                EntityStorage storage) {
    return SequencesKt.asIterable(provider.getLibraryRoots(storage.entities(provider.getEntityClass()), storage));
  }

  private static SyntheticLibraryIteratorBuilder createSyntheticLibraryIterator(List<? extends SyntheticLibraryDescriptor> librariesDescriptorsAfter,
                                                                                VirtualFile excluded) {
    for (SyntheticLibraryDescriptor lib : librariesDescriptorsAfter) {
      if (lib.contains(excluded)) {
        return new SyntheticLibraryIteratorBuilder(lib.library, lib.presentableLibraryName, Collections.singleton(excluded));
      }
    }
    return null;
  }

  @NotNull
  static Collection<? extends IndexableIteratorBuilder> getLibraryIteratorBuilders(@Nullable Collection<? extends SyntheticLibraryDescriptor> before,
                                                                                   @NotNull Collection<? extends SyntheticLibraryDescriptor> after) {
    if (after.size() == 1 && before != null && before.size() == 1) {
      SyntheticLibraryDescriptor afterLib = after.iterator().next();
      SyntheticLibraryDescriptor beforeLib = before.iterator().next();
      //fallback to logic for SyntheticLibrary without comparisonId & excludeFileCondition
      if (afterLib.comparisonId == null && beforeLib.comparisonId == null &&
          !afterLib.hasExcludeFileCondition && !beforeLib.hasExcludeFileCondition) {
        return createLibraryIteratorBuilders(beforeLib, afterLib);
      }
    }
    List<IndexableIteratorBuilder> result = new ArrayList<>();
    for (SyntheticLibraryDescriptor afterLib : after) {
      SyntheticLibraryDescriptor libForIncrementalRescanning = afterLib.getLibForIncrementalRescanning(before);
      if (libForIncrementalRescanning != null) {
        result.addAll(createLibraryIteratorBuilders(libForIncrementalRescanning, afterLib));
      }
      else {
        result.add(new SyntheticLibraryIteratorBuilder(afterLib.library, afterLib.presentableLibraryName, afterLib.getAllRoots()));
      }
    }
    return result;
  }

  @NotNull
  private static List<? extends IndexableIteratorBuilder> createLibraryIteratorBuilders(@NotNull SyntheticLibraryDescriptor beforeLib,
                                                                                        @NotNull SyntheticLibraryDescriptor afterLib) {
    Collection<VirtualFile> newRoots = ContainerUtil.subtract(afterLib.getAllRoots(), beforeLib.getAllRoots());
    if (!newRoots.isEmpty()) {
      return Collections.singletonList(
        new SyntheticLibraryIteratorBuilder(afterLib.library, afterLib.presentableLibraryName, newRoots));
    }
    else {
      return Collections.emptyList();
    }
  }

  @NotNull
  public static Collection<? extends IndexableIteratorBuilder> getIndexableSetIteratorBuilders(@Nullable IndexableSetContributorDescriptor before,
                                                                                               @NotNull IndexableSetContributorDescriptor after) {
    if (before == null) {
      return after.toIteratorBuilders();
    }
    Set<VirtualFile> applicationRootsToIndex = subtract(after.applicationRoots, before.applicationRoots);
    Set<VirtualFile> projectRootsToIndex = subtract(after.projectRoots, before.projectRoots);

    List<IndexableIteratorBuilder> result = new ArrayList<>(2);
    if (!projectRootsToIndex.isEmpty()) {
      result.add(after.toIteratorBuilderWithRoots(projectRootsToIndex, true));
    }
    if (!applicationRootsToIndex.isEmpty()) {
      result.add(after.toIteratorBuilderWithRoots(applicationRootsToIndex, false));
    }
    return result;
  }

  private static @NotNull <T> Set<T> subtract(@NotNull Collection<? extends T> from, @NotNull Collection<? extends T> what) {
    Set<T> set = new HashSet<>(from);
    set.removeAll(what);
    return set;
  }
}