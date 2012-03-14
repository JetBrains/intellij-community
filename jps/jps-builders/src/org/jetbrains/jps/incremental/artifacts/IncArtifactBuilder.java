package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.jps.artifacts.Artifact;
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
import org.jetbrains.jps.incremental.storage.BuildDataManager;

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
    Set<Artifact> affected = new HashSet<Artifact>();
    for (Artifact artifact : context.getProject().getArtifacts().values()) {
      if (context.getScope().isAffected(artifact)) {
        affected.add(artifact);
      }
    }
    final Set<Artifact> toBuild = ArtifactSorter.addIncludedArtifacts(affected, context.getProject());
    Map<String, Artifact> artifactsMap = new HashMap<String, Artifact>();
    for (Artifact artifact : toBuild) {
      artifactsMap.put(artifact.getName(), artifact);
    }

    final ArtifactSorter sorter = new ArtifactSorter(context.getProject());
    final Map<String, String> selfIncludingNameMap = sorter.getArtifactToSelfIncludingNameMap();
    for (String artifactName : sorter.getArtifactsSortedByInclusion()) {
      final Artifact artifact = artifactsMap.get(artifactName);
      if (artifact != null) {
        final String selfIncluding = selfIncludingNameMap.get(artifactName);
        if (selfIncluding != null) {
          String name = selfIncluding.equals(artifact.getName()) ? "it" : "'" + selfIncluding + "' artifact";
          context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "Cannot build '" + artifactName + "' artifact: " + name + " includes itself in the output layout"));
          break;
        }
        if (StringUtil.isEmpty(artifact.getOutputPath())) {
          context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "Cannot build '" + artifactName + "' artifact: output path is not specified"));
          break;
        }
        buildArtifact(artifact, context);
      }
    }
  }

  private static void buildArtifact(Artifact artifact, final CompileContext context) throws ProjectBuildException {
    final BuildDataManager dataManager = context.getDataManager();
    try {
      final ArtifactSourceFilesState state = dataManager.getArtifactsBuildData().getOrCreateState(artifact,
                                                                                                  context.getProject(), context.getRootsIndex());
      state.initState();
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

      final ArtifactInstructionsBuilder instructions = state.getOrCreateInstructions();
      final Set<JarInfo> changedJars = new THashSet<JarInfo>();
      instructions.processRoots(new ArtifactRootProcessor() {
        @Override
        public void process(ArtifactSourceRoot root, int rootIndex, Collection<DestinationInfo> destinations) throws IOException {
          final Set<String> sourcePaths = filesToProcess.get(rootIndex);
          if (sourcePaths == null) return;

          for (String sourcePath : sourcePaths) {
            if (!root.containsFile(sourcePath)) continue;//todo[nik] this seems to be unnecessary

            for (DestinationInfo destination : destinations) {
              if (destination instanceof ExplodedDestinationInfo) {
                root.copyFromRoot(sourcePath, rootIndex, destination.getOutputPath(), context, srcOutMapping, outSrcMapping);
              }
              else if (outSrcMapping.getState(destination.getOutputFilePath()) == null) {
                outSrcMapping.update(destination.getOutputFilePath(), Collections.<ArtifactOutputToSourceMapping.SourcePathAndRootIndex>emptyList());
                changedJars.add(((JarDestinationInfo)destination).getJarInfo());
              }
            }
          }
        }
      });

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
