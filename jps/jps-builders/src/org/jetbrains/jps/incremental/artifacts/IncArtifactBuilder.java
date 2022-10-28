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
    List<BuildTask> preprocessingTasks = createArtifactTasks(target.getArtifact(), ArtifactBuildTaskProvider.ArtifactBuildPhase.PRE_PROCESSING);
    final Set<JarInfo> missingJars = collectMissingJars(target, context);
    try {
      if (!holder.hasRemovedFiles() && !holder.hasDirtyFiles() && preprocessingTasks.isEmpty() && missingJars.isEmpty()) {
        return;
      }
    }
    catch (IOException e) {
      throw new ProjectBuildException(e);
    }

    JpsArtifact artifact = target.getArtifact();
    String outputFilePath = artifact.getOutputFilePath();
    if (StringUtil.isEmpty(outputFilePath)) {
      context.processMessage(new CompilerMessage(getBuilderName(), BuildMessage.Kind.ERROR,
                                                 JpsBuildBundle.message("build.message.cannot.build.0.artifact.output.path.is.not.specified", artifact.getName())));
      return;
    }
    final ProjectDescriptor pd = context.getProjectDescriptor();
    final ArtifactSorter sorter = new ArtifactSorter(pd.getModel());
    final Map<JpsArtifact, JpsArtifact> selfIncludingNameMap = sorter.getArtifactToSelfIncludingNameMap();
    final JpsArtifact selfIncluding = selfIncludingNameMap.get(artifact);
    if (selfIncluding != null) {
      context.processMessage(new CompilerMessage(getBuilderName(), BuildMessage.Kind.ERROR,
                                                 JpsBuildBundle.message("build.message.cannot.build.0.artifact.it.includes.itself", artifact.getName(), selfIncluding.getName(), selfIncluding.equals(artifact) ? 0 : 1)));
      return;
    }


    try {
      final Collection<String> deletedFiles = holder.getRemovedFiles(target);

      String messageText = JpsBuildBundle.message("progress.message.building.artifact.0", artifact.getName());
      context.processMessage(new ProgressMessage(messageText));
      LOG.debug(messageText);

      runArtifactTasks(preprocessingTasks, target.getArtifact(), context, ArtifactBuildTaskProvider.ArtifactBuildPhase.PRE_PROCESSING);
      final SourceToOutputMapping srcOutMapping = pd.dataManager.getSourceToOutputMap(target);
      final ArtifactOutputToSourceMapping outSrcMapping = pd.dataManager.getStorage(target, ArtifactOutToSourceStorageProvider.INSTANCE);

      final Int2ObjectMap<Set<String>> filesToProcess = new Int2ObjectOpenHashMap<>();
      final MultiMap<String, String> filesToDelete = new MultiMap<>();
      final Set<String> deletedOutputPaths = CollectionFactory.createFilePathSet();
      for (String sourcePath : deletedFiles) {
        final Collection<String> outputPaths = srcOutMapping.getOutputs(sourcePath);
        if (outputPaths != null) {
          for (String outputPath : outputPaths) {
            if (deletedOutputPaths.add(outputPath)) {
              collectSourcesCorrespondingToOutput(outputPath, sourcePath, deletedFiles, outSrcMapping, filesToProcess, filesToDelete);
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
          addFileToProcess(filesToProcess, rootIndex, sourcePath, deletedFiles);
          final Collection<String> outputPaths = srcOutMapping.getOutputs(sourcePath);
          if (outputPaths != null) {
            for (String outputPath : outputPaths) {
              if (changedOutputPaths.add(outputPath)) {
                collectSourcesCorrespondingToOutput(outputPath, sourcePath, deletedFiles, outSrcMapping, filesToProcess, filesToDelete);
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
      if (filesToDelete.isEmpty() && filesToProcess.isEmpty() && missingJars.isEmpty()) {
        return;
      }

      deleteOutdatedFiles(filesToDelete, context, srcOutMapping, outSrcMapping);
      context.checkCanceled();

      context.processMessage(new ProgressMessage(JpsBuildBundle.message("progress.message.building.artifact.0.copying.files", artifact.getName())));
      final Set<JarInfo> changedJars = new HashSet<>(missingJars);
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
      context.checkCanceled();

      JarsBuilder builder = new JarsBuilder(changedJars, context, outputConsumer, outSrcMapping);
      builder.buildJars();
      runArtifactTasks(createArtifactTasks(artifact, ArtifactBuildTaskProvider.ArtifactBuildPhase.FINISHING_BUILD), artifact, context,
                       ArtifactBuildTaskProvider.ArtifactBuildPhase.FINISHING_BUILD);
      runArtifactTasks(createArtifactTasks(artifact, ArtifactBuildTaskProvider.ArtifactBuildPhase.POST_PROCESSING), artifact, context,
                       ArtifactBuildTaskProvider.ArtifactBuildPhase.POST_PROCESSING);
    }
    catch (IOException e) {
      throw new ProjectBuildException(e);
    }
  }

  private static Set<JarInfo> collectMissingJars(@NotNull ArtifactBuildTarget target, @NotNull final CompileContext context) {
    final ProjectDescriptor pd = context.getProjectDescriptor();
    Set<JarInfo> missingJars = new HashSet<>();
    for (ArtifactRootDescriptor descriptor : pd.getBuildRootIndex().getTargetRoots(target, context)) {
      DestinationInfo destination = descriptor.getDestinationInfo();
      if (destination instanceof JarDestinationInfo) {
        JarDestinationInfo jarDestinationInfo = ((JarDestinationInfo)destination);
        if (!new File(jarDestinationInfo.getOutputFilePath()).exists()) {
          missingJars.add(jarDestinationInfo.getJarInfo());
        }
      }
    }
    return missingJars;
  }

  private static void collectSourcesCorrespondingToOutput(String outputPath, String sourcePath,
                                                          Collection<String> deletedFiles,
                                                          ArtifactOutputToSourceMapping outSrcMapping,
                                                          Int2ObjectMap<Set<String>> filesToProcess,
                                                          MultiMap<String, String> filesToDelete) throws IOException {
    filesToDelete.putValue(outputPath, sourcePath);
    final List<ArtifactOutputToSourceMapping.SourcePathAndRootIndex> sources = outSrcMapping.getState(outputPath);
    if (sources != null) {
      for (ArtifactOutputToSourceMapping.SourcePathAndRootIndex source : sources) {
        addFileToProcess(filesToProcess, source.getRootIndex(), source.getPath(), deletedFiles);
      }
    }
  }

  private static List<BuildTask> createArtifactTasks(JpsArtifact artifact, ArtifactBuildTaskProvider.ArtifactBuildPhase phase) {
    List<BuildTask> result = new ArrayList<>();
    for (ArtifactBuildTaskProvider provider : JpsServiceManager.getInstance().getExtensions(ArtifactBuildTaskProvider.class)) {
      result.addAll(provider.createArtifactBuildTasks(artifact, phase));
    }
    return result;
  }

  private static void runArtifactTasks(List<BuildTask> tasks, JpsArtifact artifact, CompileContext context,
                                       ArtifactBuildTaskProvider.ArtifactBuildPhase phase) throws ProjectBuildException {
    if (!tasks.isEmpty()) {
      context.processMessage(new ProgressMessage(JpsBuildBundle.message("progress.message.running.0.tasks.for.1.artifact",
                                                                        phase.ordinal(), artifact.getName())));
      for (BuildTask task : tasks) {
        task.build(context);
      }
    }
  }

  private static void addFileToProcess(Int2ObjectMap<Set<String>> filesToProcess,
                                       final int rootIndex,
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

  private static void deleteOutdatedFiles(MultiMap<String, String> filesToDelete, CompileContext context,
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

  @NotNull
  @Override
  public String getPresentableName() {
    return getBuilderName();
  }

  public static @Nls String getBuilderName() {
    return JpsBuildBundle.message("builder.name.artifacts.builder");
  }
}
