// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.containers.FileCollectionFactory;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.logging.ProjectBuilderLogger;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.fs.CompilationRound;
import org.jetbrains.jps.incremental.messages.DoneSomethingNotification;
import org.jetbrains.jps.incremental.messages.FileDeletedEvent;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.BuildTargetConfiguration;
import org.jetbrains.jps.incremental.storage.StampsStorage;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Eugene Zhuravlev
 */
public final class BuildOperations {
  private BuildOperations() { }

  public static void ensureFSStateInitialized(@NotNull CompileContext context, @NotNull BuildTarget<?> target, boolean readOnly) throws IOException {
    ProjectDescriptor projectDescriptor = context.getProjectDescriptor();
    BuildTargetConfiguration configuration = projectDescriptor.dataManager.getTargetStateManager().getTargetConfiguration(target);
    if (JavaBuilderUtil.isForcedRecompilationAllJavaModules(context)) {
      StampsStorage<?> stampStorage = projectDescriptor.dataManager.getFileStampStorage(target);
      FSOperations.markDirtyFiles(context, target, CompilationRound.CURRENT, stampStorage, true, null, null);
      projectDescriptor.fsState.markInitialScanPerformed(target);
      if (!readOnly) {
        configuration.save(context);
      }
    }
    else {
      boolean isTargetDirty = false;
      if (context.getScope().isBuildForced(target) ||
          (isTargetDirty = configuration.isTargetDirty(context.getProjectDescriptor())) ||
          (!projectDescriptor.getBuildRootIndex().getTargetRoots(target, context).isEmpty() && configuration.outputRootWasDeleted(context))) {
        if (isTargetDirty) {
          configuration.logDiagnostics(context);
        }
        initTargetFSState(context, target, true);
        if (!readOnly) {
          if (!context.getScope().isBuildForced(target)) {
            // case when target build is forced, is handled separately
            IncProjectBuilder.clearOutputFiles(context, target);
          }
          projectDescriptor.dataManager.cleanTargetStorages(target);
          configuration.save(context);
        }
      }
      else if (!projectDescriptor.fsState.isInitialScanPerformed(target)) {
        initTargetFSState(context, target, false);
      }
    }
  }

  @ApiStatus.Internal
  public static void initTargetFSState(CompileContext context, BuildTarget<?> target, final boolean forceMarkDirty) throws IOException {
    final ProjectDescriptor projectDescriptor = context.getProjectDescriptor();
    StampsStorage<?> stampStorage = projectDescriptor.dataManager.getFileStampStorage(target);
    Set<Path> currentFiles = FileCollectionFactory.createCanonicalPathSet();
    FSOperations.markDirtyFiles(context, target, CompilationRound.CURRENT, stampStorage, forceMarkDirty, currentFiles, null);

    // handle deleted paths
    final BuildFSState fsState = projectDescriptor.fsState;
    final SourceToOutputMapping sourceToOutputMap = projectDescriptor.dataManager.getSourceToOutputMap(target);
    for (Iterator<Path> it = sourceToOutputMap.getSourceFileIterator(); it.hasNext(); ) {
      Path file = it.next();
      if (!currentFiles.contains(file)) {
        fsState.registerDeleted(context, target, file, stampStorage);
      }
    }
    projectDescriptor.fsState.markInitialScanPerformed(target);
  }

  public static void markTargetsUpToDate(CompileContext context, BuildTargetChunk chunk) throws IOException {
    final ProjectDescriptor projectDescriptor = context.getProjectDescriptor();
    final BuildFSState fsState = projectDescriptor.fsState;
    BuildDataManager dataManager = projectDescriptor.dataManager;
    for (BuildTarget<?> target : chunk.getTargets()) {
      dataManager.getTargetStateManager().storeNonExistentOutputRoots(target, context);
    }

    if (Utils.errorsDetected(context) || context.getCancelStatus().isCanceled()) {
      return;
    }

    boolean marked = dropRemovedPaths(context, chunk);
    for (BuildTarget<?> target : chunk.getTargets()) {
      if (target instanceof ModuleBuildTarget) {
        context.clearNonIncrementalMark((ModuleBuildTarget)target);
      }

      StampsStorage<?> stampStorage = dataManager.getFileStampStorage(target);
      long targetBuildStartStamp = context.getCompilationStartStamp(target);
      for (BuildRootDescriptor buildRootDescriptor : projectDescriptor.getBuildRootIndex().getTargetRoots(target, context)) {
        marked |= fsState.markAllUpToDate(context, buildRootDescriptor, stampStorage, targetBuildStartStamp);
      }
    }

    if (marked) {
      context.processMessage(DoneSomethingNotification.INSTANCE);
    }
  }

  private static boolean dropRemovedPaths(CompileContext context, BuildTargetChunk chunk) throws IOException {
    Map<BuildTarget<?>, Collection<String>> map = Utils.REMOVED_SOURCES_KEY.get(context);
    boolean dropped = false;
    if (map != null) {
      for (BuildTarget<?> target : chunk.getTargets()) {
        Collection<String> paths = map.remove(target);
        if (paths != null) {
          SourceToOutputMapping storage = context.getProjectDescriptor().dataManager.getSourceToOutputMap(target);
          for (String path : paths) {
            storage.remove(path);
          }
          dropped = true;
        }
      }
    }
    return dropped;
  }


  /**
   * Cleans the output files corresponding to changed source files across multiple build targets.
   * Tracks and removes outdated or modified output files, manages mappings between sources and outputs,
   * and prunes empty directories if necessary.
   *
   * @param context the compilation context that provides data about the current build, such as the
   *                state of the project, output file mappings, and logging capabilities.
   * @param dirtyFilesHolder a container that holds information about files that have been modified
   *                         or deleted since the last build, organized by their associated build targets.
   * @return a map where the keys are build targets, and the values are maps between specific source files
   *         and their cleaned output file lists. The map indicates which outputs have been removed or retained for
   *         each changed source.
   */
  public static <R extends BuildRootDescriptor, T extends BuildTarget<R>>
  Map<T, Map<File, List<String>>> cleanOutputsCorrespondingToChangedFiles(final CompileContext context, DirtyFilesHolder<R, T> dirtyFilesHolder) throws ProjectBuildException {
    final BuildDataManager dataManager = context.getProjectDescriptor().dataManager;
    try {
      final Map<T, Map<File, List<String>>> sourcesToCleanedOutputByTargets = new HashMap<>();

      Set<Path> dirsToDelete = FileCollectionFactory.createCanonicalPathSet();
      final Collection<String> allDeletedOutputPaths = new ArrayList<>();

      dirtyFilesHolder.processDirtyFiles(new FileProcessor<R, T>() {
        // cache the mapping locally
        private final Map<T, SourceToOutputMapping> mappingsCache = new HashMap<>();
        private final Object2IntMap<T> idsCache = new Object2IntOpenHashMap<>();

        @Override
        public boolean apply(@NotNull T target, @NotNull File file, @NotNull R sourceRoot) throws IOException {
          SourceToOutputMapping srcToOut = mappingsCache.get(target);
          if (srcToOut == null) {
            srcToOut = dataManager.getSourceToOutputMap(target);
            mappingsCache.put(target, srcToOut);
          }
          final int targetId;
          if (!idsCache.containsKey(target)) {
            targetId = dataManager.getTargetStateManager().getBuildTargetId(target);
            idsCache.put(target, targetId);
          }
          else {
            targetId = idsCache.getInt(target);
          }

          Collection<Path> outputs = srcToOut.getOutputs(file.toPath());
          List<String> failedToDeleteOutputs = new ArrayList<>();
          if (outputs == null) {
            return true;
          }

          boolean shouldPruneOutputDirs = target instanceof ModuleBasedTarget;
          List<String> deletedForThisSource = new ArrayList<>(outputs.size());
          for (Path outputFile : outputs) {
            boolean deletedSuccessfully = deleteRecursivelyAndCollectDeleted(outputFile, deletedForThisSource, shouldPruneOutputDirs ? dirsToDelete : null);
            if (!deletedSuccessfully && Files.exists(outputFile)) {
              failedToDeleteOutputs.add(outputFile.toString());
            }
          }

          allDeletedOutputPaths.addAll(deletedForThisSource);
          dataManager.getOutputToTargetMapping().removeMappings(deletedForThisSource, targetId, srcToOut);
          Map<File, List<String>> cleaned = sourcesToCleanedOutputByTargets.computeIfAbsent(target, k -> new HashMap<>());
          cleaned.put(file, failedToDeleteOutputs);
          return true;
        }
      });

      if (!allDeletedOutputPaths.isEmpty()) {
        if (JavaBuilderUtil.isCompileJavaIncrementally(context)) {
          final ProjectBuilderLogger logger = context.getLoggingManager().getProjectBuilderLogger();
          if (logger.isEnabled()) {
            logger.logDeletedFiles(allDeletedOutputPaths);
          }
        }

        context.processMessage(new FileDeletedEvent(allDeletedOutputPaths));
      }
      // attempting to delete potentially empty directories
      FSOperations.pruneEmptyDirs(context, dirsToDelete);

      return sourcesToCleanedOutputByTargets;
    }
    catch (Exception e) {
      throw new ProjectBuildException(e);
    }
  }

  /**
   * @deprecated Use {@link #deleteRecursivelyAndCollectDeleted}
   */
  @Deprecated
  @SuppressWarnings("SSBasedInspection")
  public static boolean deleteRecursively(@NotNull String path, @NotNull Collection<String> deletedPaths, @Nullable Set<File> parentDirs) {
    Set<Path> nioParentDirs = parentDirs == null ? null : parentDirs.stream().map(file -> file.toPath()).collect(Collectors.toSet());
    return deleteRecursivelyAndCollectDeleted(Path.of(path), deletedPaths, nioParentDirs);
  }

  public static boolean deleteRecursivelyAndCollectDeleted(
    @NotNull Path file,
    @NotNull Collection<String> deletedPaths,
    @Nullable Set<Path> parentDirs
  ) {
    boolean deleted = deleteRecursively(file, deletedPaths);
    if (deleted && parentDirs != null) {
      Path parent = file.getParent();
      if (parent != null) {
        parentDirs.add(parent);
      }
    }
    return deleted;
  }

  private static boolean deleteRecursively(@NotNull Path file, @NotNull Collection<String> deletedPaths) {
    try {
      Files.walkFileTree(file, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) throws IOException {
          try {
            Files.delete(f);
          }
          catch (AccessDeniedException e) {
            if (!f.toFile().delete()) { // fallback
              throw e;
            }
          }
          deletedPaths.add(FileUtilRt.toSystemIndependentName(f.toString()));
          return FileVisitResult.CONTINUE;
        }

        @Override
        public @NotNull FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          try {
            Files.delete(dir);
          }
          catch (AccessDeniedException e) {
            if (!dir.toFile().delete()) { // fallback
              throw e;
            }
          }
          return FileVisitResult.CONTINUE;
        }
      });
      return true;
    }
    catch (IOException e) {
      return false;
    }
  }
}