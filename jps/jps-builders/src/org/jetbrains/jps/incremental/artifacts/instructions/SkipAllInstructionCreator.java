// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;

@ApiStatus.Internal
public final class SkipAllInstructionCreator extends ArtifactCompilerInstructionCreatorBase {
  public SkipAllInstructionCreator(ArtifactInstructionsBuilderImpl builder) {
    super(builder);
  }

  @Override
  protected DestinationInfo createFileDestination(@NotNull String outputFileName) {
    return null;
  }

  @Override
  protected void onAdded(ArtifactRootDescriptor descriptor) {
  }

  @Override
  protected DestinationInfo createDirectoryDestination() {
    return null;
  }

  @Override
  public SkipAllInstructionCreator subFolder(@NotNull String directoryName) {
    return this;
  }

  @Override
  public SkipAllInstructionCreator archive(@NotNull String archiveFileName) {
    return this;
  }

  @Override
  public File getTargetDirectory() {
    return null;
  }
}
