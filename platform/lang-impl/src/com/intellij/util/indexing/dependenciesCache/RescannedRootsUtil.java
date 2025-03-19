// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependenciesCache;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.ReincludedRootsUtil;
import com.intellij.util.indexing.roots.IndexableEntityProvider.IndexableIteratorBuilder;
import com.intellij.util.indexing.roots.builders.SyntheticLibraryIteratorBuilder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@ApiStatus.Internal
final class RescannedRootsUtil {
  private static final Logger LOG = Logger.getInstance(RescannedRootsUtil.class);

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

    return createBuildersForReincludedFiles(project, excludedRoots, librariesDescriptorsAfter);
  }

  private static @NotNull List<IndexableIteratorBuilder> createBuildersForReincludedFiles(@NotNull Project project,
                                                                                          @NotNull Collection<VirtualFile> reincludedRoots,
                                                                                          @NotNull List<? extends SyntheticLibraryDescriptor> librariesDescriptorsAfter) {
    ReincludedRootsUtil.Classifier classifier = ReincludedRootsUtil.classifyFiles(project, reincludedRoots);
    List<IndexableIteratorBuilder> builders = new ArrayList<>(classifier.createBuildersFromWorkspaceFiles());
    builders.addAll(classifier.createBuildersFromFilesFromIndexableSetContributors(project));
    builders.addAll(createSyntheticLibraryIteratorBuilders(librariesDescriptorsAfter,
                                                           classifier.getFilesFromAdditionalLibraryRootsProviders()));
    return builders;
  }

  private static Collection<SyntheticLibraryIteratorBuilder> createSyntheticLibraryIteratorBuilders(List<? extends SyntheticLibraryDescriptor> librariesDescriptorsAfter,
                                                                                                    Collection<VirtualFile> files) {
    if (files.isEmpty()) return Collections.emptyList();
    List<SyntheticLibraryIteratorBuilder> builders = new ArrayList<>();
    for (SyntheticLibraryDescriptor lib : librariesDescriptorsAfter) {
      List<VirtualFile> roots = new ArrayList<>();
      Iterator<VirtualFile> iterator = files.iterator();
      while (iterator.hasNext()) {
        VirtualFile file = iterator.next();
        if (lib.contains(file)) {
          roots.add(file);
          iterator.remove();
        }
      }
      if (!roots.isEmpty()) {
        builders.add(new SyntheticLibraryIteratorBuilder(lib.library, lib.presentableLibraryName, roots));
      }
      if (files.isEmpty()) {
        return builders;
      }
    }
    LOG.error("Failed fo find SyntheticLibrary roots for " + StringUtil.join(files, "\n"));
    return builders;
  }

  static @NotNull Collection<? extends IndexableIteratorBuilder> getLibraryIteratorBuilders(@Nullable Collection<? extends SyntheticLibraryDescriptor> before,
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

  private static @NotNull List<? extends IndexableIteratorBuilder> createLibraryIteratorBuilders(@NotNull SyntheticLibraryDescriptor beforeLib,
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

  public static @NotNull Collection<? extends IndexableIteratorBuilder> getIndexableSetIteratorBuilders(@Nullable IndexableSetContributorDescriptor before,
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