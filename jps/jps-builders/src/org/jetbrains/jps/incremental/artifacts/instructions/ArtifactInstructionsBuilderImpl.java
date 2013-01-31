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
package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTarget;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;

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
  private final ModuleExcludeIndex myRootsIndex;
  private int myRootIndex;
  private final IgnoredFileIndex myIgnoredFileIndex;
  private ArtifactBuildTarget myBuildTarget;

  public ArtifactInstructionsBuilderImpl(ModuleExcludeIndex rootsIndex, IgnoredFileIndex ignoredFileIndex, ArtifactBuildTarget target) {
    myRootsIndex = rootsIndex;
    myIgnoredFileIndex = ignoredFileIndex;
    myBuildTarget = target;
    myJarByPath = new HashMap<String, JarInfo>();
    myDescriptors = new ArrayList<ArtifactRootDescriptor>();
  }

  public IgnoredFileIndex getIgnoredFileIndex() {
    return myIgnoredFileIndex;
  }

  public boolean addDestination(@NotNull ArtifactRootDescriptor descriptor) {
    myDescriptors.add(descriptor);
    return true;
  }

  public ModuleExcludeIndex getRootsIndex() {
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
    return new JarBasedArtifactRootDescriptor(jarFile, pathInJar, filter, myRootIndex++, myBuildTarget, destinationInfo);
  }
}
