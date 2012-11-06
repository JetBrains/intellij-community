package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.hash.HashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.builders.impl.DirtyFilesHolderBase;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.messages.FileGeneratedEvent;
import org.jetbrains.jps.incremental.messages.UptoDateFilesSavedEvent;
import org.jetbrains.jps.incremental.storage.BuildTargetConfiguration;
import org.jetbrains.jps.incremental.storage.Timestamps;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

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
        context.processMessage(UptoDateFilesSavedEvent.INSTANCE);
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

  private static class BuildOutputConsumerImpl implements BuildOutputConsumer {
    private final BuildTarget<?> myTarget;
    private final CompileContext myContext;
    private FileGeneratedEvent myFileGeneratedEvent;
    private Collection<File> myOutputs;
    private THashSet<String> myRegisteredSources = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);

    public BuildOutputConsumerImpl(BuildTarget<?> target, CompileContext context) {
      myTarget = target;
      myContext = context;
      myFileGeneratedEvent = new FileGeneratedEvent();
      myOutputs = myTarget.getOutputRoots(context);
    }

    @Override
    public void registerOutputFile(String outputFilePath, Collection<String> sourceFiles) throws IOException {
      final File outputFile = new File(outputFilePath);
      for (File outputRoot : myOutputs) {
        if (FileUtil.isAncestor(outputRoot, outputFile, false)) {
          final String relativePath = FileUtil.getRelativePath(outputRoot, outputFile);
          if (relativePath != null) {
            myFileGeneratedEvent.add(FileUtil.toSystemIndependentName(outputRoot.getPath()), FileUtil.toSystemIndependentName(relativePath));
          }
          break;
        }
      }
      final SourceToOutputMapping mapping = myContext.getProjectDescriptor().dataManager.getSourceToOutputMap(myTarget);
      for (String sourceFile : sourceFiles) {
        if (myRegisteredSources.add(FileUtil.toSystemIndependentName(sourceFile))) {
          mapping.setOutput(sourceFile, outputFilePath);
        }
        else {
          mapping.appendOutput(sourceFile, outputFilePath);
        }
      }
    }

    public void fireFileGeneratedEvent() {
      if (!myFileGeneratedEvent.getPaths().isEmpty()) {
        myContext.processMessage(myFileGeneratedEvent);
      }
    }
  }

  public static class ChunkBuildOutputConsumerImpl implements ChunkBuildOutputConsumer {
    private final CompileContext myContext;
    private Map<BuildTarget<?>, BuildOutputConsumerImpl> myTarget2Consumer = new HashMap<BuildTarget<?>, BuildOutputConsumerImpl>();

    public ChunkBuildOutputConsumerImpl(CompileContext context) {
      myContext = context;
    }

    @Override
    public void registerOutputFile(BuildTarget<?> target, String outputFilePath, Collection<String> sourceFiles) throws IOException {
      BuildOutputConsumerImpl consumer = myTarget2Consumer.get(target);
      if (consumer == null) {
        consumer = new BuildOutputConsumerImpl(target, myContext);
        myTarget2Consumer.put(target, consumer);
      }
      consumer.registerOutputFile(outputFilePath, sourceFiles);
    }

    public void fireFileGeneratedEvents() {
      for (BuildOutputConsumerImpl consumer : myTarget2Consumer.values()) {
        consumer.fireFileGeneratedEvent();
      }
    }
  }
}
