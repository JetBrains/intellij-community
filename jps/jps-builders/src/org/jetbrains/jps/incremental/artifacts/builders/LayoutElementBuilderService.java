package org.jetbrains.jps.incremental.artifacts.builders;

import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactCompilerInstructionCreator;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactInstructionsBuilderContext;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;

/**
 * @author nik
 */
public abstract class LayoutElementBuilderService<E extends JpsPackagingElement> {
  private final Class<E> myElementClass;

  protected LayoutElementBuilderService(Class<E> elementClass) {
    myElementClass = elementClass;
  }

  public abstract void generateInstructions(E element, ArtifactCompilerInstructionCreator instructionCreator, ArtifactInstructionsBuilderContext builderContext);

  public final Class<E> getElementClass() {
    return myElementClass;
  }
}
