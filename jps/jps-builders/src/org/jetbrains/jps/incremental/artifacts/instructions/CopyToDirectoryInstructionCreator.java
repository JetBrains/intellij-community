/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import java.io.File;

/**
 * @author nik
 */
public class CopyToDirectoryInstructionCreator extends ArtifactCompilerInstructionCreatorBase {
  private final String myOutputPath;

  public CopyToDirectoryInstructionCreator(ArtifactInstructionsBuilderImpl builder, String outputPath) {
    super(builder);
    myOutputPath = outputPath;
  }

  public void addFileCopyInstruction(@NotNull File file, @NotNull String outputFileName) {
    myInstructionsBuilder.addDestination(new FileBasedArtifactSourceRoot(file, SourceFileFilter.ALL), new ExplodedDestinationInfo(myOutputPath + "/" + outputFileName));
  }

  @Override
  protected void addDirectoryCopyInstructions(ArtifactSourceRoot root) {
    myInstructionsBuilder.addDestination(root, new ExplodedDestinationInfo(myOutputPath));
  }

  public CopyToDirectoryInstructionCreator subFolder(@NotNull String directoryName) {
    return new CopyToDirectoryInstructionCreator(myInstructionsBuilder, myOutputPath + "/" + directoryName);
  }

  public ArtifactCompilerInstructionCreator archive(@NotNull String archiveFileName) {
    String jarOutputPath = myOutputPath + "/" + archiveFileName;
    final ExplodedDestinationInfo destination = new ExplodedDestinationInfo(jarOutputPath);
    final JarInfo jarInfo = new JarInfo(destination);
    if (!myInstructionsBuilder.registerJarFile(jarInfo, jarOutputPath)) {
      return new SkipAllInstructionCreator(myInstructionsBuilder);
    }
    return new PackIntoArchiveInstructionCreator(myInstructionsBuilder, jarInfo, "", destination);
  }
}
