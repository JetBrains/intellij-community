package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.ProjectLevelBuilder;
import org.jetbrains.jps.incremental.artifacts.impl.ArtifactSorter;
import org.jetbrains.jps.incremental.artifacts.impl.JarsBuilder;
import org.jetbrains.jps.incremental.artifacts.instructions.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.messages.UptoDateFilesSavedEvent;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactService;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author nik
 */
public class IncArtifactBuilder extends ProjectLevelBuilder {
  public static final String BUILDER_NAME = "artifacts";

  public IncArtifactBuilder() {
    super();
  }

  @Override
  public void build(CompileContext context) throws ProjectBuildException {
    Set<JpsArtifact> affected = new HashSet<JpsArtifact>();
    for (JpsArtifact artifact : JpsArtifactService.getInstance().getArtifacts(context.getProjectDescriptor().jpsProject)) {
      if (context.getScope().isAffected(artifact)) {
        affected.add(artifact);
      }
    }
    final Set<JpsArtifact> toBuild = ArtifactSorter.addIncludedArtifacts(affected);

    final ArtifactSorter sorter = new ArtifactSorter(context.getProjectDescriptor().jpsModel);
    final Map<JpsArtifact, JpsArtifact> selfIncludingNameMap = sorter.getArtifactToSelfIncludingNameMap();
    for (JpsArtifact artifact : sorter.getArtifactsSortedByInclusion()) {
      context.checkCanceled();
      if (toBuild.contains(artifact)) {
        final JpsArtifact selfIncluding = selfIncludingNameMap.get(artifact);
        if (selfIncluding != null) {
          String name = selfIncluding.equals(artifact) ? "it" : "'" + selfIncluding.getName() + "' artifact";
          context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "Cannot build '" + artifact.getName() + "' artifact: " + name + " includes itself in the output layout"));
          break;
        }
        if (StringUtil.isEmpty(artifact.getOutputPath())) {
          context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "Cannot build '" + artifact.getName() + "' artifact: output path is not specified"));
          break;
        }
        buildArtifact(artifact, context);
      }
    }
  }

  private static void buildArtifact(JpsArtifact artifact, final CompileContext context) throws ProjectBuildException {
    final ProjectDescriptor pd = context.getProjectDescriptor();
    try {
      final ArtifactSourceFilesState state = pd.dataManager.getArtifactsBuildData().getOrCreateState(artifact, pd.project, pd.jpsModel, pd.rootsIndex);
      state.initState(pd.dataManager);
      final Set<String> deletedFiles = state.getDeletedFiles();
      final Map<String,IntArrayList> changedFiles = state.getChangedFiles();
      if (deletedFiles.isEmpty() && changedFiles.isEmpty()) {
        return;
      }

      context.processMessage(new ProgressMessage("Building artifact '" + artifact.getName() + "'..."));
      final ArtifactSourceToOutputMapping srcOutMapping = state.getOrCreateSrcOutMapping();
      final ArtifactOutputToSourceMapping outSrcMapping = state.getOrCreateOutSrcMapping();

      final TIntObjectHashMap<Set<String>> filesToProcess = new TIntObjectHashMap<Set<String>>();
      MultiMap<String, String> filesToDelete = new MultiMap<String, String>();
      for (String sourcePath : deletedFiles) {
        final List<String> outputPaths = srcOutMapping.getState(sourcePath);
        if (outputPaths != null) {
          for (String outputPath : outputPaths) {
            filesToDelete.putValue(outputPath, sourcePath);
            final List<ArtifactOutputToSourceMapping.SourcePathAndRootIndex> sources = outSrcMapping.getState(outputPath);
            if (sources != null) {
              for (ArtifactOutputToSourceMapping.SourcePathAndRootIndex source : sources) {
                addFileToProcess(filesToProcess, source.getRootIndex(), source.getPath(), deletedFiles);
              }
            }
          }
        }
      }

      Set<String> changedOutputPaths = new THashSet<String>();
      for (Map.Entry<String, IntArrayList> entry : changedFiles.entrySet()) {
        final IntArrayList roots = entry.getValue();
        final String sourcePath = entry.getKey();
        for (int i = 0; i < roots.size(); i++) {
          addFileToProcess(filesToProcess, roots.get(i), sourcePath, deletedFiles);
        }
        final List<String> outputPaths = srcOutMapping.getState(sourcePath);
        if (outputPaths != null) {
          changedOutputPaths.addAll(outputPaths);
          for (String outputPath : outputPaths) {
            final List<ArtifactOutputToSourceMapping.SourcePathAndRootIndex> sources = outSrcMapping.getState(outputPath);
            if (sources != null) {
              for (ArtifactOutputToSourceMapping.SourcePathAndRootIndex source : sources) {
                addFileToProcess(filesToProcess, source.getRootIndex(), source.getPath(), deletedFiles);
              }
            }
          }
        }
      }
      for (String sourcePath : changedFiles.keySet()) {
        srcOutMapping.remove(sourcePath);
      }
      for (String outputPath : changedOutputPaths) {
        outSrcMapping.remove(outputPath);
      }

      deleteOutdatedFiles(filesToDelete, context, srcOutMapping, outSrcMapping);
      context.checkCanceled();

      final ArtifactInstructionsBuilder instructions = state.getOrCreateInstructions();
      final Set<JarInfo> changedJars = new THashSet<JarInfo>();
      instructions.processRoots(new ArtifactRootProcessor() {
        @Override
        public boolean process(ArtifactSourceRoot root, DestinationInfo destination) throws IOException {
          if (context.getCancelStatus().isCanceled()) return false;

          final Set<String> sourcePaths = filesToProcess.get(root.getRootIndex());
          if (sourcePaths == null) return true;

          for (String sourcePath : sourcePaths) {
            if (destination instanceof ExplodedDestinationInfo) {
              root.copyFromRoot(sourcePath, root.getRootIndex(), destination.getOutputPath(), context, srcOutMapping, outSrcMapping);
            }
            else if (outSrcMapping.getState(destination.getOutputFilePath()) == null) {
              outSrcMapping.update(destination.getOutputFilePath(), Collections.<ArtifactOutputToSourceMapping.SourcePathAndRootIndex>emptyList());
              changedJars.add(((JarDestinationInfo)destination).getJarInfo());
            }
          }
          return true;
        }
      });
      context.checkCanceled();

      JarsBuilder builder = new JarsBuilder(changedJars, context, srcOutMapping, outSrcMapping, instructions);
      final boolean processed = builder.buildJars();
      if (!processed) {
        return;
      }

      state.updateTimestamps();
      state.markUpToDate();
      context.processMessage(UptoDateFilesSavedEvent.INSTANCE);
    }
    catch (IOException e) {
      throw new ProjectBuildException(e);
    }
  }

  private static void addFileToProcess(TIntObjectHashMap<Set<String>> filesToProcess,
                                       final int rootIndex,
                                       final String path,
                                       Set<String> deletedFiles) {
    if (deletedFiles.contains(path)) {
      return;
    }
    Set<String> paths = filesToProcess.get(rootIndex);
    if (paths == null) {
      paths = new THashSet<String>();
      filesToProcess.put(rootIndex, paths);
    }
    paths.add(path);
  }

  private static void deleteOutdatedFiles(MultiMap<String, String> filesToDelete, CompileContext context,
                                          ArtifactSourceToOutputMapping srcOutMapping,
                                          ArtifactOutputToSourceMapping outSrcMapping) throws IOException {
    if (filesToDelete.isEmpty()) return;

    context.processMessage(new ProgressMessage("Deleting outdated files..."));
    int notDeletedFilesCount = 0;
    final THashSet<String> notDeletedPaths = new THashSet<String>();
    final THashSet<String> deletedPaths = new THashSet<String>();

    for (String filePath : filesToDelete.keySet()) {
      if (notDeletedPaths.contains(filePath)) {
        continue;
      }

      boolean deleted = deletedPaths.contains(filePath);
      if (!deleted) {
        deleted = FileUtil.delete(new File(FileUtil.toSystemDependentName(filePath)));
      }

      if (deleted) {
        context.getLoggingManager().getArtifactBuilderLogger().fileDeleted(filePath);
        outSrcMapping.remove(filePath);
        deletedPaths.add(filePath);
        for (String sourcePath : filesToDelete.get(filePath)) {
          srcOutMapping.removeValue(sourcePath, filePath);
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
  }

  @Override
  public String getName() {
    return BUILDER_NAME;
  }

  @Override
  public String getDescription() {
    return "Artifacts builder";
  }
}
