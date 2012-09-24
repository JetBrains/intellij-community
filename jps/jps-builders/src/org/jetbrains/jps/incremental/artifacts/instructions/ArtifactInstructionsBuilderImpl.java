package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.IgnoredFilePatterns;
import org.jetbrains.jps.incremental.ModuleRootsIndex;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class ArtifactInstructionsBuilderImpl implements ArtifactInstructionsBuilder {
  private final Map<String, JarInfo> myJarByPath;
  private final List<ArtifactRootDescriptor> myDescriptors;
  private final ModuleRootsIndex myRootsIndex;
  private int myRootIndex;
  private ArtifactBuildTarget myBuildTarget;

  public ArtifactInstructionsBuilderImpl(ModuleRootsIndex rootsIndex, ArtifactBuildTarget target) {
    myRootsIndex = rootsIndex;
    myBuildTarget = target;
    myJarByPath = new HashMap<String, JarInfo>();
    myDescriptors = new ArrayList<ArtifactRootDescriptor>();
  }

  public IgnoredFilePatterns getIgnoredFilePatterns() {
    return myRootsIndex.getIgnoredFilePatterns();
  }

  public boolean addDestination(@NotNull ArtifactRootDescriptor descriptor) {
    @NotNull DestinationInfo destinationInfo = descriptor.getDestinationInfo();
    if (destinationInfo instanceof ExplodedDestinationInfo && descriptor instanceof FileBasedArtifactRootDescriptor
        && FileUtil.filesEqual(descriptor.getRootFile(), new File(FileUtil.toSystemDependentName(destinationInfo.getOutputFilePath())))) {
      return false;
    }

    myDescriptors.add(descriptor);
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
  public List<ArtifactRootDescriptor> getDescriptors() {
    return myDescriptors;
  }

  public FileBasedArtifactRootDescriptor createFileBasedRoot(@NotNull File file,
                                                             @NotNull SourceFileFilter filter,
                                                             final DestinationInfo destinationInfo) {
    return new FileBasedArtifactRootDescriptor(file, filter, myRootIndex++, myBuildTarget, destinationInfo);
  }

  public JarBasedArtifactRootDescriptor createJarBasedRoot(@NotNull File jarFile,
                                                           @NotNull String pathInJar,
                                                           @NotNull SourceFileFilter filter, final DestinationInfo destinationInfo) {
    return new JarBasedArtifactRootDescriptor(jarFile, pathInJar, filter, myRootIndex, myBuildTarget, destinationInfo);
  }
}
