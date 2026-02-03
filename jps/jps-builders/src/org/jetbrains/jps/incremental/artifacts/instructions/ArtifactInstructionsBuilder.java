// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;

import java.io.File;
import java.util.List;

@ApiStatus.Internal
public interface ArtifactInstructionsBuilder {
  @NotNull
  List<ArtifactRootDescriptor> getDescriptors();

  @NotNull
  FileCopyingHandler createCopyingHandler(@NotNull File file, @NotNull JpsPackagingElement contextElement, @NotNull ArtifactCompilerInstructionCreator instructionCreator);
}
