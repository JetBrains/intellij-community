package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.LinkedMultiMap;
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
  private final Map<String, JarInfo> myJarByPath;
  private final MultiMap<ArtifactSourceRoot, DestinationInfo> myInstructions;
  private final ModuleRootsIndex myRootsIndex;
  private final IgnoredFilePatterns myIgnoredFilePatterns;

  public ArtifactInstructionsBuilderImpl(ModuleRootsIndex rootsIndex, IgnoredFilePatterns patterns) {
    myRootsIndex = rootsIndex;
    myIgnoredFilePatterns = patterns;
    myJarByPath = new HashMap<String, JarInfo>();
    myInstructions = new LinkedMultiMap<ArtifactSourceRoot, DestinationInfo>();
  }

  public IgnoredFilePatterns getIgnoredFilePatterns() {
    return myIgnoredFilePatterns;
  }

  public boolean addDestination(@NotNull ArtifactSourceRoot root, @NotNull DestinationInfo destinationInfo) {
    if (destinationInfo instanceof ExplodedDestinationInfo && root instanceof FileBasedArtifactSourceRoot
        && root.getRootFile().equals(new File(FileUtil.toSystemDependentName(destinationInfo.getOutputFilePath())))) {
      return false;
    }

    myInstructions.putValue(root, destinationInfo);
    return true;
  }

  public ModuleRootsIndex getRootsIndex() {
    return myRootsIndex;
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
  public int getRootIndex(@NotNull ArtifactSourceRoot root) {
    int i = 0;
    for (Map.Entry<ArtifactSourceRoot, Collection<DestinationInfo>> entry : myInstructions.entrySet()) {
      if (entry.getKey().equals(root)) {
        return i;
      }
      i++;
    }
    return -1;
  }

  @Override
  public void processRoots(ArtifactRootProcessor processor) throws IOException {
    int i = 0;
    for (Map.Entry<ArtifactSourceRoot, Collection<DestinationInfo>> entry : myInstructions.entrySet()) {
      if (!processor.process(entry.getKey(), i, entry.getValue())) {
        break;
      }
      i++;
    }
  }
}
