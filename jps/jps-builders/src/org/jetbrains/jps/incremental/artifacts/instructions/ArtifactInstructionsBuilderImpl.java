package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.IgnoredFilePatterns;
import org.jetbrains.jps.incremental.ModuleRootsIndex;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class ArtifactInstructionsBuilderImpl implements ArtifactInstructionsBuilder {
  private final Map<String, ArtifactSourceRoot> mySourceByOutput;
  private final Map<String, JarInfo> myJarByPath;
  private final MultiMap<ArtifactSourceRoot, DestinationInfo> myInstructions;
  private final ModuleRootsIndex myRootsIndex;
  private final IgnoredFilePatterns myIgnoredFilePatterns;

  public ArtifactInstructionsBuilderImpl(ModuleRootsIndex rootsIndex, IgnoredFilePatterns patterns) {
    myRootsIndex = rootsIndex;
    myIgnoredFilePatterns = patterns;
    mySourceByOutput = new HashMap<String, ArtifactSourceRoot>();
    myJarByPath = new HashMap<String, JarInfo>();
    myInstructions = new MultiMap<ArtifactSourceRoot, DestinationInfo>();
  }

  public IgnoredFilePatterns getIgnoredFilePatterns() {
    return myIgnoredFilePatterns;
  }

  public boolean addDestination(@NotNull ArtifactSourceRoot root, @NotNull DestinationInfo destinationInfo) {
    if (destinationInfo instanceof ExplodedDestinationInfo && root instanceof FileBasedArtifactSourceRoot
        && root.getRootFile().equals(new File(FileUtil.toSystemDependentName(destinationInfo.getOutputFilePath())))) {
      return false;
    }

    if (checkOutputPath(destinationInfo.getOutputPath(), root)) {
      myInstructions.putValue(root, destinationInfo);
      return true;
    }
    return false;
  }

  public ModuleRootsIndex getRootsIndex() {
    return myRootsIndex;
  }

  public boolean checkOutputPath(final String outputPath, final ArtifactSourceRoot sourceFile) {
    //todo[nik] combine intersecting roots
    //ArtifactSourceRoot old = mySourceByOutput.get(outputPath);
    //if (old == null) {
    //  mySourceByOutput.put(outputPath, sourceFile);
    //  return true;
    //}
    //todo[nik] show warning?
    return true;
  }

  public boolean registerJarFile(@NotNull JarInfo jarInfo, @NotNull String outputPath) {
    if (myJarByPath.containsKey(outputPath)) {
      return false;
    }
    myJarByPath.put(outputPath, jarInfo);
    return true;
  }

  @Override
  @Nullable
  public JarInfo getJarInfo(String outputPath) {
    return myJarByPath.get(outputPath);
  }

  @Override
  public void processRoots(ArtifactRootProcessor processor) throws IOException {
    for (Map.Entry<ArtifactSourceRoot, Collection<DestinationInfo>> entry : myInstructions.entrySet()) {
      processor.process(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public void processContainingRoots(String filePath, ArtifactRootProcessor processor) throws IOException {
    //todo[nik] improve?
    for (Map.Entry<ArtifactSourceRoot, Collection<DestinationInfo>> entry : myInstructions.entrySet()) {
      final ArtifactSourceRoot root = entry.getKey();
      if (root.containsFile(filePath)) {
        processor.process(root, entry.getValue());
      }
    }
  }
}
