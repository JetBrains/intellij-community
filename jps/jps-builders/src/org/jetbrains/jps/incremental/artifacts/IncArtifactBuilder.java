package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
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
      final Set<String> changedFiles = state.getChangedFiles();
      if (deletedFiles.isEmpty() && changedFiles.isEmpty()) {
        return;
      }

      context.processMessage(new ProgressMessage("Building artifact '" + artifact.getName() + "'..."));
      final ArtifactSourceToOutputMapping mapping = state.getOrCreateMapping();
      final Set<String> deletedJars = deleteOutdatedFiles(deletedFiles, context, mapping);
      final ArtifactInstructionsBuilder instructions = state.getOrCreateInstructions();
      final Set<JarInfo> changedJars = new THashSet<JarInfo>();
      for (String deletedJar : deletedJars) {
        ContainerUtil.addIfNotNull(instructions.getJarInfo(deletedJar), changedJars);
      }

      Map<String, String[]> updatedMappings = new HashMap<String, String[]>();
      for (final String filePath : changedFiles) {
        final List<String> outputs = new SmartList<String>();
        instructions.processContainingRoots(filePath, new ArtifactRootProcessor() {
          @Override
          public void process(ArtifactSourceRoot root, Collection<DestinationInfo> destinations) throws IOException {
            for (DestinationInfo destination : destinations) {
              if (destination instanceof ExplodedDestinationInfo) {
                context.getLoggingManager().getArtifactBuilderLogger().fileCopied(filePath);
                root.copyFromRoot(filePath, destination.getOutputPath(), outputs);
              }
              else {
                outputs.add(destination.getOutputFilePath() + JarPathUtil.JAR_SEPARATOR);
                changedJars.add(((JarDestinationInfo)destination).getJarInfo());
              }
            }
          }
        });
        updatedMappings.put(filePath, ArrayUtil.toStringArray(outputs));
      }

      JarsBuilder builder = new JarsBuilder(changedJars, null, context);
      final boolean processed = builder.buildJars(new THashSet<String>());
      if (!processed) {
        return;
      }

      state.updateTimestamps();
      for (String filePath : deletedFiles) {
        mapping.remove(filePath);
      }
      for (Map.Entry<String, String[]> entry : updatedMappings.entrySet()) {
        mapping.update(entry.getKey(), entry.getValue());
      }
      state.markUpToDate();
      context.processMessage(UptoDateFilesSavedEvent.INSTANCE);
    }
    catch (IOException e) {
      throw new ProjectBuildException(e);
    }
  }

  private static Set<String> deleteOutdatedFiles(Set<String> deletedFiles, CompileContext context,
                                                 ArtifactSourceToOutputMapping mapping) throws IOException {
    if (deletedFiles.isEmpty()) return Collections.emptySet();

    context.processMessage(new ProgressMessage("Deleting outdated files..."));
    Set<String> pathsToDelete = new THashSet<String>();
    for (String path : deletedFiles) {
      final String[] outputPaths = mapping.getState(path);
      Collections.addAll(pathsToDelete, outputPaths);
    }

    int notDeletedFilesCount = 0;
    final THashSet<String> notDeletedJars = new THashSet<String>();
    final THashSet<String> deletedJars = new THashSet<String>();

    for (String fullPath : pathsToDelete) {
      int end = fullPath.indexOf(JarPathUtil.JAR_SEPARATOR);
      boolean isJar = end != -1;
      String filePath = isJar ? fullPath.substring(0, end) : fullPath;
      boolean deleted = false;
      if (isJar) {
        if (notDeletedJars.contains(filePath)) {
          continue;
        }
        deleted = deletedJars.contains(filePath);
      }

      File file = new File(FileUtil.toSystemDependentName(filePath));
      if (!deleted) {
        deleted = FileUtil.delete(file);
      }

      if (deleted) {
        context.getLoggingManager().getArtifactBuilderLogger().fileDeleted(filePath);
        if (isJar) {
          deletedJars.add(filePath);
        }
      }
      else {
        if (isJar) {
          notDeletedJars.add(filePath);
        }
        if (notDeletedFilesCount++ > 50) {
          context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.WARNING, "Deletion of outdated files stopped because too many files cannot be deleted"));
          break;
        }
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.WARNING, "Cannot delete file '" + filePath + "'"));
      }
    }

    return deletedJars;
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
