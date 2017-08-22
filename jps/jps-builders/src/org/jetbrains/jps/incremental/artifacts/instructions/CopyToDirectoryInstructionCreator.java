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
import org.jetbrains.annotations.Nullable;

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

  protected DestinationInfo createFileDestination(@NotNull String outputFileName) {
    return new ExplodedDestinationInfo(myOutputPath + "/" + outputFileName);
  }

  @Override
  protected ExplodedDestinationInfo createDirectoryDestination() {
    return new ExplodedDestinationInfo(myOutputPath);
  }

  @Override
  protected void onAdded(ArtifactRootDescriptor descriptor) {
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

  @Nullable
  @Override
  public File getTargetDirectory() {
    return new File(myOutputPath);
  }
}
