/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.impl.BuildOutputConsumerImpl;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.builders.impl.DirtyFilesHolderBase;
import org.jetbrains.jps.builders.logging.ProjectBuilderLogger;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.messages.DoneSomethingNotification;
import org.jetbrains.jps.incremental.messages.FileDeletedEvent;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.BuildTargetConfiguration;
import org.jetbrains.jps.incremental.storage.Timestamps;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/30/12
 */
public class BuildOperations {
  private BuildOperations() {
  }

  public static void ensureFSStateInitialized(CompileContext context, BuildTarget<?> target) throws IOException {
    final ProjectDescriptor pd = context.getProjectDescriptor();
    final Timestamps timestamps = pd.timestamps.getStorage();
    final BuildTargetConfiguration configuration = pd.getTargetsState().getTargetConfiguration(target);
    if (context.isProjectRebuild()) {
      FSOperations.markDirtyFiles(context, target, timestamps, true, null, null);
      pd.fsState.markInitialScanPerformed(target);
      configuration.save(context);
    }
    else if (context.getScope().isBuildForced(target) || configuration.isTargetDirty(context) || configuration.outputRootWasDeleted(context)) {
      initTargetFSState(context, target, true);
      IncProjectBuilder.clearOutputFiles(context, target);
      pd.dataManager.cleanTargetStorages(target);
      configuration.save(context);
    }
    else if (!pd.fsState.isInitialScanPerformed(target)) {
      initTargetFSState(context, target, false);
    }
  }

  private static void initTargetFSState(CompileContext context, BuildTarget<?> target, final boolean forceMarkDirty) throws IOException {
    final ProjectDescriptor pd = context.getProjectDescriptor();
    final Timestamps timestamps = pd.timestamps.getStorage();
    final THashSet<File> currentFiles = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
    FSOperations.markDirtyFiles(context, target, timestamps, forceMarkDirty, currentFiles, null);

    // handle deleted paths
    final BuildFSState fsState = pd.fsState;
    fsState.clearDeletedPaths(target);
    final SourceToOutputMapping sourceToOutputMap = pd.dataManager.getSourceToOutputMap(target);
    for (final Iterator<String> it = sourceToOutputMap.getSourcesIterator(); it.hasNext(); ) {
      final String path = it.next();
      // can check if the file exists
      final File file = new File(path);
      if (!currentFiles.contains(file)) {
        fsState.registerDeleted(target, file, timestamps);
      }
    }
    pd.fsState.markInitialScanPerformed(target);
  }

  public static <R extends BuildRootDescriptor, T extends BuildTarget<R>>
  void buildTarget(final T target, final CompileContext context, TargetBuilder<?, ?> builder) throws ProjectBuildException, IOException {

    if (builder.getTargetTypes().contains(target.getTargetType())) {
      DirtyFilesHolder<R, T> holder = new DirtyFilesHolderBase<R, T>(context) {
        @Override
        public void processDirtyFiles(@NotNull FileProcessor<R, T> processor) throws IOException {
          context.getProjectDescriptor().fsState.processFilesToRecompile(context, target, processor);
        }
      };
      //noinspection unchecked
      BuildOutputConsumerImpl outputConsumer = new BuildOutputConsumerImpl(target, context);
      ((TargetBuilder<R, T>)builder).build(target, holder, outputConsumer, context);
      outputConsumer.fireFileGeneratedEvent();
      context.checkCanceled();
    }
  }

  public static void markTargetsUpToDate(CompileContext context, BuildTargetChunk chunk) throws IOException {
    final ProjectDescriptor pd = context.getProjectDescriptor();
    final BuildFSState fsState = pd.fsState;
    for (BuildTarget<?> target : chunk.getTargets()) {
      pd.getTargetsState().getTargetConfiguration(target).storeNonexistentOutputRoots(context);
    }
    if (!Utils.errorsDetected(context) && !context.getCancelStatus().isCanceled()) {
      boolean marked = dropRemovedPaths(context, chunk);
      for (BuildTarget<?> target : chunk.getTargets()) {
        if (target instanceof ModuleBuildTarget) {
          context.clearNonIncrementalMark((ModuleBuildTarget)target);
        }
        final Timestamps timestamps = pd.timestamps.getStorage();
        for (BuildRootDescriptor rd : pd.getBuildRootIndex().getTargetRoots(target, context)) {
          marked |= fsState.markAllUpToDate(context, rd, timestamps);
        }
      }

      if (marked) {
        context.processMessage(DoneSomethingNotification.INSTANCE);
      }
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
      final Map<T, Set<File>> cleanedSources = new java.util.HashMap<T, Set<File>>();

      final THashSet<File> dirsToDelete = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
      final Collection<String> deletedPaths = new ArrayList<String>();

      dirtyFilesHolder.processDirtyFiles(new FileProcessor<R, T>() {
        private final Map<T, SourceToOutputMapping> mappingsCache = new java.util.HashMap<T, SourceToOutputMapping>(); // cache the mapping locally

        @Override
        public boolean apply(T target, File file, R sourceRoot) throws IOException {
          SourceToOutputMapping srcToOut = mappingsCache.get(target);
          if (srcToOut == null) {
            srcToOut = dataManager.getSourceToOutputMap(target);
            mappingsCache.put(target, srcToOut);
          }
          final String srcPath = file.getPath();
          final Collection<String> outputs = srcToOut.getOutputs(srcPath);
          if (outputs != null) {
            final boolean shouldPruneOutputDirs = target instanceof ModuleBasedTarget;
            for (String output : outputs) {
              deleteRecursively(output, deletedPaths, shouldPruneOutputDirs ? dirsToDelete : null);
            }
            Set<File> cleaned = cleanedSources.get(target);
            if (cleaned == null) {
              cleaned = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
              cleanedSources.put(target, cleaned);
            }
            cleaned.add(file);
          }
          return true;
        }

      });

      if (context.isMake()) {
        final ProjectBuilderLogger logger = context.getLoggingManager().getProjectBuilderLogger();
        if (logger.isEnabled()) {
          logger.logDeletedFiles(deletedPaths);
        }
      }

      if (!deletedPaths.isEmpty()) {
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

  public static boolean deleteRecursively(@NotNull String path, @NotNull Collection<String> deletedPaths, @Nullable Set<File> parentDirs) {
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

  private static boolean deleteRecursively(File file, Collection<String> deletedPaths) {
    File[] children = file.listFiles();
    if (children != null) {
      for (File child : children) {
        deleteRecursively(child, deletedPaths);
      }
    }
    boolean deleted = file.delete();
    if (deleted && children == null) {
      deletedPaths.add(FileUtil.toSystemIndependentName(file.getPath()));
    }
    return deleted;
  }
}
