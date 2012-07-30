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
  private final List<Pair<ArtifactRootDescriptor, DestinationInfo>> myInstructions;
  private final ModuleRootsIndex myRootsIndex;
  private final IgnoredFilePatterns myIgnoredFilePatterns;
  private final int myArtifactId;
  private final String myArtifactName;
  private int myRootIndex;

  public ArtifactInstructionsBuilderImpl(ModuleRootsIndex rootsIndex, IgnoredFilePatterns patterns, int artifactId, String artifactName) {
    myRootsIndex = rootsIndex;
    myIgnoredFilePatterns = patterns;
    myArtifactId = artifactId;
    myArtifactName = artifactName;
    myJarByPath = new HashMap<String, JarInfo>();
    myInstructions = new ArrayList<Pair<ArtifactRootDescriptor, DestinationInfo>>();
  }

  public IgnoredFilePatterns getIgnoredFilePatterns() {
    return myIgnoredFilePatterns;
  }

  public boolean addDestination(@NotNull ArtifactRootDescriptor descriptor, @NotNull DestinationInfo destinationInfo) {
    if (destinationInfo instanceof ExplodedDestinationInfo && descriptor instanceof FileBasedArtifactRootDescriptor
        && descriptor.getRootFile().equals(new File(FileUtil.toSystemDependentName(destinationInfo.getOutputFilePath())))) {
      return false;
    }

    myInstructions.add(Pair.create(descriptor, destinationInfo));
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
    for (Pair<ArtifactRootDescriptor, DestinationInfo> pair : myInstructions) {
      if (!processor.process(pair.getFirst(), pair.getSecond())) {
        break;
      }
    }
  }

  @Override
  public List<Pair<ArtifactRootDescriptor, DestinationInfo>> getInstructions() {
    return myInstructions;
  }

  public FileBasedArtifactRootDescriptor createFileBasedRoot(@NotNull File file,
                                                         @NotNull SourceFileFilter filter) {
    return new FileBasedArtifactRootDescriptor(file, filter, myRootIndex++, myArtifactId, myArtifactName);
  }

  public JarBasedArtifactRootDescriptor createJarBasedRoot(@NotNull File jarFile,
                                                       @NotNull String pathInJar,
                                                       @NotNull SourceFileFilter filter) {
    return new JarBasedArtifactRootDescriptor(jarFile, pathInJar, filter, myRootIndex, myArtifactId, myArtifactName);
  }
}
