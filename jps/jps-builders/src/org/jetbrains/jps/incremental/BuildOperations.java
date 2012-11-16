package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
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

  public static void ensureFSStateInitialized(CompileContext context, BuildTargetChunk chunk) throws IOException {
    for (BuildTarget<?> target : chunk.getTargets()) {
      ensureFSStateInitialized(context, target);
    }
  }

  public static void ensureFSStateInitialized(CompileContext context, BuildTarget<?> target) throws IOException {
    final ProjectDescriptor pd = context.getProjectDescriptor();
    final Timestamps timestamps = pd.timestamps.getStorage();
    final BuildTargetConfiguration configuration = pd.getTargetsState().getTargetConfiguration(target);

    if (context.isProjectRebuild()) {
      FSOperations.markDirtyFiles(context, target, timestamps, true, null);
      configuration.save();
    }
    else if (context.getScope().isRecompilationForced(target) || configuration.isTargetDirty()) {
      if (target instanceof ModuleBuildTarget) {
        // Using special FSState initialization, because for correct results of "integrate" operation of JavaBuilder
        // we still need to know which sources were deleted from previous compilation
        initTargetFSState(context, target, true);
      }
      else {
        IncProjectBuilder.clearOutputFiles(context, target);
        FSOperations.markDirtyFiles(context, target, timestamps, true, null);
      }
      configuration.save();
    }
    else if (pd.fsState.markInitialScanPerformed(target)) {
      initTargetFSState(context, target, false);
    }
  }

  private static void initTargetFSState(CompileContext context, BuildTarget<?> target, final boolean forceMarkDirty) throws IOException {
    final ProjectDescriptor pd = context.getProjectDescriptor();
    final Timestamps timestamps = pd.timestamps.getStorage();
    final THashSet<File> currentFiles = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
    FSOperations.markDirtyFiles(context, target, timestamps, forceMarkDirty, currentFiles);

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
    if (!Utils.errorsDetected(context) && !context.getCancelStatus().isCanceled()) {
      boolean marked = dropRemovedPaths(context, chunk);
      for (BuildTarget<?> target : chunk.getTargets()) {
        if (context.isMake() && target instanceof ModuleBuildTarget) {
          // ensure non-incremental flag cleared
          context.clearNonIncrementalMark((ModuleBuildTarget)target);
        }
        if (context.isProjectRebuild()) {
          fsState.markInitialScanPerformed(target);
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

      ProjectBuilderLogger logger = context.getLoggingManager().getProjectBuilderLogger();
      final Collection<String> outputsToLog = logger.isEnabled() ? new LinkedList<String>() : null;
      final THashSet<File> dirsToDelete = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);

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
              if (outputsToLog != null) {
                outputsToLog.add(output);
              }
              final File outFile = new File(output);
              final boolean deleted = outFile.delete();
              if (deleted && shouldPruneOutputDirs) {
                final File parent = outFile.getParentFile();
                if (parent != null) {
                  dirsToDelete.add(parent);
                }
              }
            }
            if (!outputs.isEmpty()) {
              context.processMessage(new FileDeletedEvent(outputs));
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

      if (outputsToLog != null && context.isMake()) {
        logger.logDeletedFiles(outputsToLog);
      }
      // attempting to delete potentially empty directories
      FSOperations.pruneEmptyDirs(context, dirsToDelete);

      return cleanedSources;
    }
    catch (Exception e) {
      throw new ProjectBuildException(e);
    }
  }
}
