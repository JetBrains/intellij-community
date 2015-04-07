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
package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
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

/**
 * @author nik
 */
public class IncArtifactBuilder extends TargetBuilder<ArtifactRootDescriptor, ArtifactBuildTarget> {
  private static final Logger LOG = Logger.getInstance(IncArtifactBuilder.class);
  public static final String BUILDER_NAME = "Artifacts builder";

  public IncArtifactBuilder() {
    super(Collections.singletonList(ArtifactBuildTargetType.INSTANCE));
  }

  @Override
  public void build(@NotNull ArtifactBuildTarget target,
                    @NotNull DirtyFilesHolder<ArtifactRootDescriptor, ArtifactBuildTarget> holder,
                    @NotNull BuildOutputConsumer outputConsumer, @NotNull final CompileContext context) throws ProjectBuildException {
    JpsArtifact artifact = target.getArtifact();
    String outputFilePath = artifact.getOutputFilePath();
    if (StringUtil.isEmpty(outputFilePath)) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "Cannot build '" + artifact.getName() + "' artifact: output path is not specified"));
      return;
    }
    final ProjectDescriptor pd = context.getProjectDescriptor();
    final ArtifactSorter sorter = new ArtifactSorter(pd.getModel());
    final Map<JpsArtifact, JpsArtifact> selfIncludingNameMap = sorter.getArtifactToSelfIncludingNameMap();
    final JpsArtifact selfIncluding = selfIncludingNameMap.get(artifact);
    if (selfIncluding != null) {
      String name = selfIncluding.equals(artifact) ? "it" : "'" + selfIncluding.getName() + "' artifact";
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "Cannot build '" + artifact.getName() + "' artifact: " + name + " includes itself in the output layout"));
      return;
    }


    try {
      final Collection<String> deletedFiles = holder.getRemovedFiles(target);

      String messageText = "Building artifact '" + artifact.getName() + "'...";
      context.processMessage(new ProgressMessage(messageText));
      LOG.debug(messageText);

      runArtifactTasks(context, target.getArtifact(), ArtifactBuildTaskProvider.ArtifactBuildPhase.PRE_PROCESSING);
      final SourceToOutputMapping srcOutMapping = pd.dataManager.getSourceToOutputMap(target);
      final ArtifactOutputToSourceMapping outSrcMapping = pd.dataManager.getStorage(target, ArtifactOutToSourceStorageProvider.INSTANCE);

      final TIntObjectHashMap<Set<String>> filesToProcess = new TIntObjectHashMap<Set<String>>();
      final MultiMap<String, String> filesToDelete = new MultiMap<String, String>();
      final Set<String> deletedOutputPaths = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
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

      final Set<String> changedOutputPaths = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
      holder.processDirtyFiles(new FileProcessor<ArtifactRootDescriptor, ArtifactBuildTarget>() {
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
      if (filesToDelete.isEmpty() && filesToProcess.isEmpty()) {
        return;
      }

      deleteOutdatedFiles(filesToDelete, context, srcOutMapping, outSrcMapping);
      context.checkCanceled();

      context.processMessage(new ProgressMessage("Building artifact '" + artifact.getName() + "': copying files..."));
      final Set<JarInfo> changedJars = new THashSet<JarInfo>();
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
              outSrcMapping.update(destination.getOutputFilePath(),
                                   Collections.<ArtifactOutputToSourceMapping.SourcePathAndRootIndex>emptyList());
              changedJars.add(((JarDestinationInfo)destination).getJarInfo());
            }
          }
        }
      }
      context.checkCanceled();

      JarsBuilder builder = new JarsBuilder(changedJars, context, outputConsumer, outSrcMapping);
      builder.buildJars();
      runArtifactTasks(context, artifact, ArtifactBuildTaskProvider.ArtifactBuildPhase.FINISHING_BUILD);
      runArtifactTasks(context, artifact, ArtifactBuildTaskProvider.ArtifactBuildPhase.POST_PROCESSING);
    }
    catch (IOException e) {
      throw new ProjectBuildException(e);
    }
  }

  private static void collectSourcesCorrespondingToOutput(String outputPath, String sourcePath,
                                                          Collection<String> deletedFiles,
                                                          ArtifactOutputToSourceMapping outSrcMapping,
                                                          TIntObjectHashMap<Set<String>> filesToProcess,
                                                          MultiMap<String, String> filesToDelete) throws IOException {
    filesToDelete.putValue(outputPath, sourcePath);
    final List<ArtifactOutputToSourceMapping.SourcePathAndRootIndex> sources = outSrcMapping.getState(outputPath);
    if (sources != null) {
      for (ArtifactOutputToSourceMapping.SourcePathAndRootIndex source : sources) {
        addFileToProcess(filesToProcess, source.getRootIndex(), source.getPath(), deletedFiles);
      }
    }
  }

  private static void runArtifactTasks(CompileContext context, JpsArtifact artifact, ArtifactBuildTaskProvider.ArtifactBuildPhase phase)
    throws ProjectBuildException {
    for (ArtifactBuildTaskProvider provider : JpsServiceManager.getInstance().getExtensions(ArtifactBuildTaskProvider.class)) {
      List<? extends BuildTask> tasks = provider.createArtifactBuildTasks(artifact, phase);
      if (!tasks.isEmpty()) {
        context.processMessage(new ProgressMessage("Running " + phase.getPresentableName() + " tasks for '" + artifact.getName() + "' artifact..."));
        for (BuildTask task : tasks) {
          task.build(context);
        }
      }
    }
  }

  private static void addFileToProcess(TIntObjectHashMap<Set<String>> filesToProcess,
                                       final int rootIndex,
                                       final String path,
                                       Collection<String> deletedFiles) {
    if (deletedFiles.contains(path)) {
      return;
    }
    Set<String> paths = filesToProcess.get(rootIndex);
    if (paths == null) {
      paths = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
      filesToProcess.put(rootIndex, paths);
    }
    paths.add(path);
  }

  private static void deleteOutdatedFiles(MultiMap<String, String> filesToDelete, CompileContext context,
                                          SourceToOutputMapping srcOutMapping,
                                          ArtifactOutputToSourceMapping outSrcMapping) throws IOException {
    if (filesToDelete.isEmpty()) return;

    context.processMessage(new ProgressMessage("Deleting outdated files..."));
    int notDeletedFilesCount = 0;
    final THashSet<String> notDeletedPaths = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
    final THashSet<String> deletedPaths = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);

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
          context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.WARNING, "Deletion of outdated files stopped because too many files cannot be deleted"));
          break;
        }
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.WARNING, "Cannot delete file '" + filePath + "'"));
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
    return BUILDER_NAME;
  }
}
