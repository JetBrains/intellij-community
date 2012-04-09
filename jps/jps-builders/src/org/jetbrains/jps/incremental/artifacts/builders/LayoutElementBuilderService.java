package org.jetbrains.jps.incremental.artifacts.builders;

import org.jetbrains.jps.artifacts.LayoutElement;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactCompilerInstructionCreator;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactInstructionsBuilderContext;

/**
 * @author nik
 */
public abstract class LayoutElementBuilderService<E extends LayoutElement> {
  private final Class<E> myElementClass;

  protected LayoutElementBuilderService(Class<E> elementClass) {
    myElementClass = elementClass;
  }

  public abstract void generateInstructions(E element, ArtifactCompilerInstructionCreator instructionCreator, ArtifactInstructionsBuilderContext builderContext);

  public final Class<E> getElementClass() {
    return myElementClass;
  }
}
