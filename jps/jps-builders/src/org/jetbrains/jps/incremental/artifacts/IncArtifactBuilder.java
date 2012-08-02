package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.artifacts.impl.ArtifactSorter;
import org.jetbrains.jps.incremental.artifacts.impl.JarsBuilder;
import org.jetbrains.jps.incremental.artifacts.instructions.*;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;

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
    JpsBuilderArtifactService artifactService = JpsBuilderArtifactService.getInstance();
    JpsModel model = context.getProjectDescriptor().jpsModel;
    for (JpsArtifact artifact : artifactService.getArtifacts(model, false)) {
      if (context.getScope().isAffected(artifact)) {
        affected.add(artifact);
      }
    }
    affected.addAll(artifactService.getSyntheticArtifacts(model));
    final Set<JpsArtifact> toBuild = ArtifactSorter.addIncludedArtifacts(affected);

    final ArtifactSorter sorter = new ArtifactSorter(model);
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
      final ArtifactSourceFilesState state = pd.dataManager.getArtifactsBuildData().getOrCreateState(artifact, pd);
      state.ensureFsStateInitialized(pd.dataManager, context);
      final Collection<String> deletedFiles = pd.fsState.getAndClearDeletedPaths(artifact.getName());
      final Map<Integer, Set<String>> filesToRecompile = pd.fsState.getFilesToRecompile(artifact.getName());
      if (deletedFiles.isEmpty() && filesToRecompile.isEmpty()) {
        state.markUpToDate(context);
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
      for (Map.Entry<Integer, Set<String>> entry : filesToRecompile.entrySet()) {
        int rootIndex = entry.getKey();
        for (String sourcePath : entry.getValue()) {
          addFileToProcess(filesToProcess, rootIndex, sourcePath, deletedFiles);
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
      }
      for (Set<String> paths : filesToRecompile.values()) {
        for (String sourcePath : paths) {
          srcOutMapping.remove(sourcePath);
        }
      }
      for (String outputPath : changedOutputPaths) {
        outSrcMapping.remove(outputPath);
      }

      deleteOutdatedFiles(filesToDelete, context, srcOutMapping, outSrcMapping);
      context.checkCanceled();

      final ArtifactInstructionsBuilder instructions = pd.getArtifactRootsIndex().getInstructionsBuilder(artifact);
      final Set<JarInfo> changedJars = new THashSet<JarInfo>();
      instructions.processRoots(new ArtifactRootProcessor() {
        @Override
        public boolean process(ArtifactRootDescriptor descriptor, DestinationInfo destination) throws IOException {
          if (context.getCancelStatus().isCanceled()) return false;

          final Set<String> sourcePaths = filesToProcess.get(descriptor.getRootIndex());
          if (sourcePaths == null) return true;

          for (String sourcePath : sourcePaths) {
            if (destination instanceof ExplodedDestinationInfo) {
              descriptor.copyFromRoot(sourcePath, descriptor.getRootIndex(), destination.getOutputPath(), context,
                                      srcOutMapping, outSrcMapping);
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
      if (processed && !Utils.errorsDetected(context) && !context.getCancelStatus().isCanceled()) {
        state.markUpToDate(context);
      }
    }
    catch (IOException e) {
      throw new ProjectBuildException(e);
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
  public void buildStarted(final CompileContext context) {
    context.addBuildListener(new BuildListener() {
      @Override
      public void filesGenerated(Collection<Pair<String, String>> paths) {
        BuildFSState fsState = context.getProjectDescriptor().fsState;
        ArtifactRootsIndex rootsIndex = context.getProjectDescriptor().getArtifactRootsIndex();
        for (Pair<String, String> pair : paths) {
          File file = new File(pair.getFirst(), pair.getSecond());
          for (ArtifactRootDescriptor descriptor : rootsIndex.getDescriptors(file)) {
            fsState.markRecompile(descriptor, FileUtil.toSystemIndependentName(file.getAbsolutePath()));
          }
        }
      }

      @Override
      public void filesDeleted(Collection<String> paths) {
        BuildFSState state = context.getProjectDescriptor().fsState;
        ArtifactRootsIndex index = context.getProjectDescriptor().getArtifactRootsIndex();
        for (String path : paths) {
          File file = new File(FileUtil.toSystemDependentName(path));
          for (ArtifactRootDescriptor descriptor : index.getDescriptors(file)) {
            state.registerDeleted(descriptor.getArtifactName(), path);
          }
        }
      }
    });
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
