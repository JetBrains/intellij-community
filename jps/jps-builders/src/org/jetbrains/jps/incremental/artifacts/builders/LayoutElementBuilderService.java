// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.artifacts.builders;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.TargetOutputIndex;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactCompilerInstructionCreator;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactInstructionsBuilderContext;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;

import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
public abstract class LayoutElementBuilderService<E extends JpsPackagingElement> {
  private final Class<E> myElementClass;

  protected LayoutElementBuilderService(Class<E> elementClass) {
    myElementClass = elementClass;
  }

  public abstract void generateInstructions(E element, ArtifactCompilerInstructionCreator instructionCreator, ArtifactInstructionsBuilderContext builderContext);

  public Collection<? extends BuildTarget<?>> getDependencies(@NotNull E element, TargetOutputIndex outputIndex) {
    return List.of();
  }

  public final Class<E> getElementClass() {
    return myElementClass;
  }
}
