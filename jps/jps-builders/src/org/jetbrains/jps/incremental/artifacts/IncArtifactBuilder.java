// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.MultiMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.JpsBuildBundle;
import org.jetbrains.jps.builders.artifacts.ArtifactBuildTaskProvider;
import org.jetbrains.jps.builders.artifacts.impl.ArtifactOutToSourceStorageProvider;
import org.jetbrains.jps.builders.logging.ProjectBuilderLogger;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.artifacts.impl.ArtifactSorter;
import org.jetbrains.jps.incremental.artifacts.impl.JarsBuilder;
import org.jetbrains.jps.incremental.artifacts.instructions.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class IncArtifactBuilder extends TargetBuilder<ArtifactRootDescriptor, ArtifactBuildTarget> {
  private static final Logger LOG = Logger.getInstance(IncArtifactBuilder.class);
  public static final String BUILDER_ID = "artifacts-builder";

  public IncArtifactBuilder() {
    super(Collections.singletonList(ArtifactBuildTargetType.INSTANCE));
  }

  @Override
  public void build(@NotNull ArtifactBuildTarget target,
                    @NotNull DirtyFilesHolder<ArtifactRootDescriptor, ArtifactBuildTarget> holder,
                    @NotNull BuildOutputConsumer outputConsumer, @NotNull final CompileContext context) throws ProjectBuildException {
    try {
      new IncArtifactBuilderHelper(target, outputConsumer, context).build(holder);
    } catch (IOException e) {
      throw new ProjectBuildException(e);
    }
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return getBuilderName();
  }

  public static @Nls String getBuilderName() {
    return JpsBuildBundle.message("builder.name.artifacts.builder");
  }

  private static class IncArtifactBuilderHelper {
    private final @NotNull ArtifactBuildTarget target;
    private final @NotNull JpsArtifact artifact;
    private final @NotNull BuildOutputConsumer outputConsumer;
    private final @NotNull CompileContext context;
    private final ProjectDescriptor pd;
    private final ArtifactOutputToSourceMapping outSrcMapping;

    private final Int2ObjectMap<Set<String>> filesToProcess = new Int2ObjectOpenHashMap<>();
    private final Set<JarInfo> changedJars = new HashSet<>();

    private IncArtifactBuilderHelper(@NotNull ArtifactBuildTarget target,
                                     @NotNull BuildOutputConsumer consumer,
                                     @NotNull CompileContext context) throws IOException {
      this.target = target;
      this.artifact = target.getArtifact();
      this.outputConsumer = consumer;
      this.context = context;
      this.pd = context.getProjectDescriptor();
      this.outSrcMapping = pd.dataManager.getStorage(target, ArtifactOutToSourceStorageProvider.INSTANCE);
    }

    public void build(DirtyFilesHolder<ArtifactRootDescriptor, ArtifactBuildTarget> holder) throws ProjectBuildException, IOException {
      if (startBuild()) {
        createAndRunArtifactTasks(ArtifactBuildTaskProvider.ArtifactBuildPhase.PRE_PROCESSING);

        processDirtyFiles(holder);

        collectMissingFiles();
        processFiles();

        collectMissingJars();
        buildJars();

        createAndRunArtifactTasks(ArtifactBuildTaskProvider.ArtifactBuildPhase.FINISHING_BUILD);
        createAndRunArtifactTasks(ArtifactBuildTaskProvider.ArtifactBuildPhase.POST_PROCESSING);
      }
    }

    private boolean startBuild() {
      String outputFilePath = artifact.getOutputFilePath();
      if (StringUtil.isEmpty(outputFilePath)) {
        context.processMessage(new CompilerMessage(getBuilderName(), BuildMessage.Kind.ERROR,
                                                   JpsBuildBundle.message("build.message.cannot.build.0.artifact.output.path.is.not.specified", artifact.getName())));
        return false;
      }
      final ArtifactSorter sorter = new ArtifactSorter(pd.getModel());
      final Map<JpsArtifact, JpsArtifact> selfIncludingNameMap = sorter.getArtifactToSelfIncludingNameMap();
      final JpsArtifact selfIncluding = selfIncludingNameMap.get(artifact);
      if (selfIncluding != null) {
        context.processMessage(new CompilerMessage(getBuilderName(), BuildMessage.Kind.ERROR,
                                                   JpsBuildBundle.message("build.message.cannot.build.0.artifact.it.includes.itself", artifact.getName(), selfIncluding.getName(), selfIncluding.equals(artifact) ? 0 : 1)));
        return false;
      }

      String messageText = JpsBuildBundle.message("progress.message.building.artifact.0", artifact.getName());
      context.processMessage(new ProgressMessage(messageText));
      LOG.debug(messageText);

      return true;
    }

    private void processDirtyFiles(DirtyFilesHolder<ArtifactRootDescriptor, ArtifactBuildTarget> holder) throws ProjectBuildException, IOException {
      context.checkCanceled();

      if (!holder.hasRemovedFiles() && !holder.hasDirtyFiles()) {
        return;
      }

      final Collection<String> deletedFiles = holder.getRemovedFiles(target);

      final SourceToOutputMapping srcOutMapping = pd.dataManager.getSourceToOutputMap(target);

      final MultiMap<String, String> filesToDelete = new MultiMap<>();
      final Set<String> deletedOutputPaths = CollectionFactory.createFilePathSet();
      for (String sourcePath : deletedFiles) {
        final Collection<String> outputPaths = srcOutMapping.getOutputs(sourcePath);
        if (outputPaths != null) {
          for (String outputPath : outputPaths) {
            if (deletedOutputPaths.add(outputPath)) {
              collectSourcesCorrespondingToOutput(outputPath, sourcePath, deletedFiles, outSrcMapping, filesToDelete);
            }
          }
        }
      }

      final Set<String> changedOutputPaths = CollectionFactory.createFilePathSet();
      holder.processDirtyFiles(new FileProcessor<>() {
        @Override
        public boolean apply(ArtifactBuildTarget target, File file, ArtifactRootDescriptor root) throws IOException {
          int rootIndex = root.getRootIndex();
          String sourcePath = FileUtil.toSystemIndependentName(file.getPath());
          addFileToProcess(rootIndex, sourcePath, deletedFiles);
          final Collection<String> outputPaths = srcOutMapping.getOutputs(sourcePath);
          if (outputPaths != null) {
            for (String outputPath : outputPaths) {
              if (changedOutputPaths.add(outputPath)) {
                collectSourcesCorrespondingToOutput(outputPath, sourcePath, deletedFiles, outSrcMapping, filesToDelete);
              }
            }
          }
          return true;
        }
      });

      BuildOperations.cleanOutputsCorrespondingToChangedFiles(context, holder);
      for (String outputPath : changedOutputPaths) {
        outSrcMapping.remove(outputPath);
      }

      deleteOutdatedFiles(filesToDelete, srcOutMapping, outSrcMapping);
    }

    private void collectMissingFiles() throws ProjectBuildException, IOException {
      context.checkCanceled();

      final ProjectDescriptor pd = context.getProjectDescriptor();
      for (ArtifactRootDescriptor descriptor : pd.getBuildRootIndex().getTargetRoots(target, context)) {
        DestinationInfo destination = descriptor.getDestinationInfo();
        if (destination instanceof ExplodedDestinationInfo) {
          ExplodedDestinationInfo explodedDestinationInfo = ((ExplodedDestinationInfo)destination);
          if (!new File(explodedDestinationInfo.getOutputFilePath()).exists()) {
            String outputPath = explodedDestinationInfo.getOutputFilePath();
            final List<ArtifactOutputToSourceMapping.SourcePathAndRootIndex> sources = outSrcMapping.getState(outputPath);
            if (sources != null) {
              for (ArtifactOutputToSourceMapping.SourcePathAndRootIndex source : sources) {
                addFileToProcess(source.getRootIndex(), source.getPath(), List.of());
                outSrcMapping.remove(outputPath);
              }
            }
          }
        }
      }
    }

    private void collectMissingJars() throws ProjectBuildException {
      context.checkCanceled();

      final ProjectDescriptor pd = context.getProjectDescriptor();
      for (ArtifactRootDescriptor descriptor : pd.getBuildRootIndex().getTargetRoots(target, context)) {
        DestinationInfo destination = descriptor.getDestinationInfo();
        if (destination instanceof JarDestinationInfo) {
          JarDestinationInfo jarDestinationInfo = ((JarDestinationInfo)destination);
          if (!new File(jarDestinationInfo.getOutputFilePath()).exists()) {
            changedJars.add(jarDestinationInfo.getJarInfo());
          }
        }
      }
    }

    private void processFiles() throws ProjectBuildException, IOException {
      if (filesToProcess.isEmpty()) {
        return;
      }

      context.processMessage(new ProgressMessage(JpsBuildBundle.message("progress.message.building.artifact.0.copying.files", artifact.getName())));
      for (ArtifactRootDescriptor descriptor : pd.getBuildRootIndex().getTargetRoots(target, context)) {
        context.checkCanceled();
        final Set<String> sourcePaths = filesToProcess.get(descriptor.getRootIndex());
        if (sourcePaths == null) continue;

        for (String sourcePath : sourcePaths) {
          if (!descriptor.getFilter().shouldBeCopied(sourcePath, pd)) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("File " + sourcePath + " will be skipped because it isn't accepted by filter");
            }
            continue;
          }
          DestinationInfo destination = descriptor.getDestinationInfo();
          if (destination instanceof ExplodedDestinationInfo) {
            descriptor.copyFromRoot(sourcePath, descriptor.getRootIndex(), destination.getOutputPath(), context,
                                    outputConsumer, outSrcMapping);
          }
          else {
            List<ArtifactOutputToSourceMapping.SourcePathAndRootIndex> sources = outSrcMapping.getState(destination.getOutputFilePath());
            if (sources == null || sources.size() > 0 && sources.get(0).getRootIndex() == descriptor.getRootIndex()) {
              outSrcMapping.update(destination.getOutputFilePath(), Collections.emptyList());
              changedJars.add(((JarDestinationInfo)destination).getJarInfo());
            }
          }
        }
      }
    }

    private void buildJars() throws ProjectBuildException, IOException {
      context.checkCanceled();
      JarsBuilder builder = new JarsBuilder(changedJars, context, outputConsumer, outSrcMapping);
      builder.buildJars();
    }

    private void collectSourcesCorrespondingToOutput(String outputPath, String sourcePath,
                                                     Collection<String> deletedFiles,
                                                     ArtifactOutputToSourceMapping outSrcMapping,
                                                     MultiMap<String, String> filesToDelete) throws IOException {
      filesToDelete.putValue(outputPath, sourcePath);
      final List<ArtifactOutputToSourceMapping.SourcePathAndRootIndex> sources = outSrcMapping.getState(outputPath);
      if (sources != null) {
        for (ArtifactOutputToSourceMapping.SourcePathAndRootIndex source : sources) {
          addFileToProcess(source.getRootIndex(), source.getPath(), deletedFiles);
        }
      }
    }

    private List<BuildTask> createArtifactTasks(ArtifactBuildTaskProvider.ArtifactBuildPhase phase) {
      List<BuildTask> result = new ArrayList<>();
      for (ArtifactBuildTaskProvider provider : JpsServiceManager.getInstance().getExtensions(ArtifactBuildTaskProvider.class)) {
        result.addAll(provider.createArtifactBuildTasks(artifact, phase));
      }
      return result;
    }

    private void runArtifactTasks(List<BuildTask> tasks, ArtifactBuildTaskProvider.ArtifactBuildPhase phase) throws ProjectBuildException {
      if (!tasks.isEmpty()) {
        context.processMessage(new ProgressMessage(JpsBuildBundle.message("progress.message.running.0.tasks.for.1.artifact",
                                                                          phase.ordinal(), artifact.getName())));
        for (BuildTask task : tasks) {
          context.checkCanceled();
          task.build(context);
        }
      }
    }

    private void createAndRunArtifactTasks(ArtifactBuildTaskProvider.ArtifactBuildPhase phase) throws ProjectBuildException {
      runArtifactTasks(createArtifactTasks(phase), phase);
    }

    private void addFileToProcess(final int rootIndex,
                                  final String path,
                                  Collection<String> deletedFiles) {
      if (deletedFiles.contains(path)) {
        return;
      }
      Set<String> paths = filesToProcess.get(rootIndex);
      if (paths == null) {
        paths = CollectionFactory.createFilePathSet();
        filesToProcess.put(rootIndex, paths);
      }
      paths.add(path);
    }

    private void deleteOutdatedFiles(MultiMap<String, String> filesToDelete,
                                     SourceToOutputMapping srcOutMapping,
                                     ArtifactOutputToSourceMapping outSrcMapping) throws IOException {
      if (filesToDelete.isEmpty()) return;

      context.processMessage(new ProgressMessage(JpsBuildBundle.message("progress.message.deleting.outdated.files")));
      int notDeletedFilesCount = 0;
      Set<String> notDeletedPaths = CollectionFactory.createFilePathSet();
      Set<String> deletedPaths = CollectionFactory.createFilePathSet();

      for (String filePath : filesToDelete.keySet()) {
        if (notDeletedPaths.contains(filePath)) {
          continue;
        }

        boolean deleted = deletedPaths.contains(filePath);
        if (!deleted) {
          deleted = FileUtil.delete(new File(filePath));
        }

        if (deleted) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Outdated output file deleted: " + filePath);
          }
          outSrcMapping.remove(filePath);
          deletedPaths.add(filePath);
          for (String sourcePath : filesToDelete.get(filePath)) {
            srcOutMapping.removeOutput(sourcePath, filePath);
          }
        }
        else {
          notDeletedPaths.add(filePath);
          if (notDeletedFilesCount++ > 50) {
            context.processMessage(new CompilerMessage(getBuilderName(), BuildMessage.Kind.WARNING,
                                                       JpsBuildBundle.message("build.message.deletion.of.outdated.files.stopped")));
            break;
          }
          context.processMessage(new CompilerMessage(getBuilderName(), BuildMessage.Kind.WARNING,
                                                     JpsBuildBundle.message("build.message.cannot.delete.file.0", filePath)));
        }
      }
      ProjectBuilderLogger logger = context.getLoggingManager().getProjectBuilderLogger();
      if (logger.isEnabled()) {
        logger.logDeletedFiles(deletedPaths);
      }
    }
  }
}
