package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.IgnoredFilePatterns;
import org.jetbrains.jps.incremental.ModuleRootsIndex;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class ArtifactInstructionsBuilderImpl implements ArtifactInstructionsBuilder {
  private final Map<String, JarInfo> myJarByPath;
  private final List<Pair<ArtifactSourceRoot, DestinationInfo>> myInstructions;
  private final ModuleRootsIndex myRootsIndex;
  private final IgnoredFilePatterns myIgnoredFilePatterns;
  private int myRootIndex;

  public ArtifactInstructionsBuilderImpl(ModuleRootsIndex rootsIndex, IgnoredFilePatterns patterns) {
    myRootsIndex = rootsIndex;
    myIgnoredFilePatterns = patterns;
    myJarByPath = new HashMap<String, JarInfo>();
    myInstructions = new ArrayList<Pair<ArtifactSourceRoot, DestinationInfo>>();
  }

  public IgnoredFilePatterns getIgnoredFilePatterns() {
    return myIgnoredFilePatterns;
  }

  public boolean addDestination(@NotNull ArtifactSourceRoot root, @NotNull DestinationInfo destinationInfo) {
    if (destinationInfo instanceof ExplodedDestinationInfo && root instanceof FileBasedArtifactSourceRoot
        && root.getRootFile().equals(new File(FileUtil.toSystemDependentName(destinationInfo.getOutputFilePath())))) {
      return false;
    }

    myInstructions.add(Pair.create(root, destinationInfo));
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
  public void processRoots(ArtifactRootProcessor processor) throws IOException {
    for (Pair<ArtifactSourceRoot, DestinationInfo> pair : myInstructions) {
      if (!processor.process(pair.getFirst(), pair.getSecond())) {
        break;
      }
    }
  }

  public FileBasedArtifactSourceRoot createFileBasedRoot(@NotNull File file,
                                                         @NotNull SourceFileFilter filter) {
    return new FileBasedArtifactSourceRoot(file, filter, myRootIndex++);
  }

  public JarBasedArtifactSourceRoot createJarBasedRoot(@NotNull File jarFile,
                                                       @NotNull String pathInJar,
                                                       @NotNull SourceFileFilter filter) {
    return new JarBasedArtifactSourceRoot(jarFile, pathInJar, filter, myRootIndex++);
  }
}
