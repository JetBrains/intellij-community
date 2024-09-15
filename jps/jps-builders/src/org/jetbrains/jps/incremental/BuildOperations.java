// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.containers.FileCollectionFactory;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
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

/**
 * @author Eugene Zhuravlev
 */
public final class BuildOperations {
  private BuildOperations() { }

  public static void ensureFSStateInitialized(@NotNull CompileContext context, @NotNull BuildTarget<?> target, boolean readOnly) throws IOException {
    ProjectDescriptor projectDescriptor = context.getProjectDescriptor();
    BuildTargetConfiguration configuration = projectDescriptor.getTargetsState().getTargetConfiguration(target);
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
      if (context.getScope().isBuildForced(target) || (isTargetDirty = configuration.isTargetDirty(context.getProjectDescriptor())) || (!projectDescriptor.getBuildRootIndex().getTargetRoots(target, context).isEmpty() && configuration.outputRootWasDeleted(context))) {
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

  private static void initTargetFSState(CompileContext context, BuildTarget<?> target, final boolean forceMarkDirty) throws IOException {
    final ProjectDescriptor projectDescriptor = context.getProjectDescriptor();
    StampsStorage<?> stampStorage = projectDescriptor.dataManager.getFileStampStorage(target);
    Set<File> currentFiles = FileCollectionFactory.createCanonicalFileSet();
    FSOperations.markDirtyFiles(context, target, CompilationRound.CURRENT, stampStorage, forceMarkDirty, currentFiles, null);

    // handle deleted paths
    final BuildFSState fsState = projectDescriptor.fsState;
    final SourceToOutputMapping sourceToOutputMap = projectDescriptor.dataManager.getSourceToOutputMap(target);
    for (final Iterator<String> it = sourceToOutputMap.getSourcesIterator(); it.hasNext(); ) {
      final String path = it.next();
      // can check if the file exists
      final File file = new File(path);
      if (!currentFiles.contains(file)) {
        fsState.registerDeleted(context, target, file, stampStorage);
      }
    }
    projectDescriptor.fsState.markInitialScanPerformed(target);
  }

  public static void markTargetsUpToDate(CompileContext context, BuildTargetChunk chunk) throws IOException {
    final ProjectDescriptor projectDescriptor = context.getProjectDescriptor();
    final BuildFSState fsState = projectDescriptor.fsState;
    for (BuildTarget<?> target : chunk.getTargets()) {
      projectDescriptor.getTargetsState().getTargetConfiguration(target).storeNonexistentOutputRoots(context);
    }

    if (Utils.errorsDetected(context) || context.getCancelStatus().isCanceled()) {
      return;
    }

    boolean marked = dropRemovedPaths(context, chunk);
    for (BuildTarget<?> target : chunk.getTargets()) {
      if (target instanceof ModuleBuildTarget) {
        context.clearNonIncrementalMark((ModuleBuildTarget)target);
      }

      StampsStorage<?> stampStorage = projectDescriptor.dataManager.getFileStampStorage(target);
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
    final Map<BuildTarget<?>, Collection<String>> map = Utils.REMOVED_SOURCES_KEY.get(context);
    boolean dropped = false;
    if (map != null) {
      for (BuildTarget<?> target : chunk.getTargets()) {
        final Collection<String> paths = map.remove(target);
        if (paths != null) {
          final SourceToOutputMapping storage = context.getProjectDescriptor().dataManager.getSourceToOutputMap(target);
          for (String path : paths) {
            storage.remove(path);
            dropped = true;
          }
        }
      }
    }
    return dropped;
  }

  public static <R extends BuildRootDescriptor, T extends BuildTarget<R>>
  Map<T, Set<File>> cleanOutputsCorrespondingToChangedFiles(final CompileContext context, DirtyFilesHolder<R, T> dirtyFilesHolder) throws ProjectBuildException {
    final BuildDataManager dataManager = context.getProjectDescriptor().dataManager;
    try {
      final Map<T, Set<File>> cleanedSources = new HashMap<>();

      Set<File> dirsToDelete = FileCollectionFactory.createCanonicalFileSet();
      final Collection<String> deletedPaths = new ArrayList<>();

      dirtyFilesHolder.processDirtyFiles(new FileProcessor<R, T>() {
        private final Map<T, SourceToOutputMapping> mappingsCache = new HashMap<>(); // cache the mapping locally
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
            targetId = dataManager.getTargetsState().getBuildTargetId(target);
            idsCache.put(target, targetId);
          }
          else {
            targetId = idsCache.getInt(target);
          }
          final String srcPath = file.getPath();
          final Collection<String> outputs = srcToOut.getOutputs(srcPath);
          if (outputs != null) {
            final boolean shouldPruneOutputDirs = target instanceof ModuleBasedTarget;
            final List<String> deletedForThisSource = new ArrayList<>(outputs.size());
            for (String output : outputs) {
              deleteRecursively(output, deletedForThisSource, shouldPruneOutputDirs ? dirsToDelete : null);
            }
            deletedPaths.addAll(deletedForThisSource);
            dataManager.getOutputToTargetRegistry().removeMapping(deletedForThisSource, targetId);
            Set<File> cleaned = cleanedSources.get(target);
            if (cleaned == null) {
              cleaned = FileCollectionFactory.createCanonicalFileSet();
              cleanedSources.put(target, cleaned);
            }
            cleaned.add(file);
          }
          return true;
        }

      });

      if (!deletedPaths.isEmpty()) {
        if (JavaBuilderUtil.isCompileJavaIncrementally(context)) {
          final ProjectBuilderLogger logger = context.getLoggingManager().getProjectBuilderLogger();
          if (logger.isEnabled()) {
            logger.logDeletedFiles(deletedPaths);
          }
        }

        context.processMessage(new FileDeletedEvent(deletedPaths));
      }
      // attempting to delete potentially empty directories
      FSOperations.pruneEmptyDirs(context, dirsToDelete);

      return cleanedSources;
    }
    catch (Exception e) {
      throw new ProjectBuildException(e);
    }
  }

  public static boolean deleteRecursively(@NotNull String path, @NotNull Collection<? super String> deletedPaths, @Nullable Set<? super File> parentDirs) {
    File file = new File(path);
    boolean deleted = deleteRecursively(file, deletedPaths);
    if (deleted && parentDirs != null) {
      File parent = file.getParentFile();
      if (parent != null) {
        parentDirs.add(parent);
      }
    }
    return deleted;
  }

  private static boolean deleteRecursively(final File file, final Collection<? super String> deletedPaths) {
    try {
      Files.walkFileTree(file.toPath(), EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
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
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
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