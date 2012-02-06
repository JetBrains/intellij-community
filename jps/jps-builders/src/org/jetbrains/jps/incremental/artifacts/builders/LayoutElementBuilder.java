package org.jetbrains.jps.incremental.artifacts.builders;

import org.jetbrains.jps.artifacts.LayoutElement;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactCompilerInstructionCreator;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactInstructionsBuilderContext;

/**
 * @author nik
 */
public abstract class LayoutElementBuilder<E extends LayoutElement> {
  public abstract void generateInstructions(E element, ArtifactCompilerInstructionCreator instructionCreator, ArtifactInstructionsBuilderContext builderContext);
}
