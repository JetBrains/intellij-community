// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

@ApiStatus.Internal
public final class CopyToDirectoryInstructionCreator extends ArtifactCompilerInstructionCreatorBase {
  private final String myOutputPath;

  public CopyToDirectoryInstructionCreator(ArtifactInstructionsBuilderImpl builder, String outputPath) {
    super(builder);
    myOutputPath = outputPath;
  }

  @Override
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

  @Override
  public CopyToDirectoryInstructionCreator subFolder(@NotNull String directoryName) {
    return new CopyToDirectoryInstructionCreator(myInstructionsBuilder, myOutputPath + "/" + directoryName);
  }

  @Override
  public ArtifactCompilerInstructionCreator archive(@NotNull String archiveFileName) {
    String jarOutputPath = myOutputPath + "/" + archiveFileName;
    final ExplodedDestinationInfo destination = new ExplodedDestinationInfo(jarOutputPath);
    final JarInfo jarInfo = new JarInfo(destination);
    if (!myInstructionsBuilder.registerJarFile(jarInfo, jarOutputPath)) {
      return new SkipAllInstructionCreator(myInstructionsBuilder);
    }
    return new PackIntoArchiveInstructionCreator(myInstructionsBuilder, jarInfo, "", destination);
  }

  @Override
  public @Nullable File getTargetDirectory() {
    return new File(myOutputPath);
  }
}
