package org.jetbrains.jps.incremental.artifacts.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.TargetOutputIndex;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactCompilerInstructionCreator;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactInstructionsBuilderContext;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;

import java.util.Collection;
import java.util.Collections;

/**
 * @author nik
 */
public abstract class LayoutElementBuilderService<E extends JpsPackagingElement> {
  private final Class<E> myElementClass;

  protected LayoutElementBuilderService(Class<E> elementClass) {
    myElementClass = elementClass;
  }

  public abstract void generateInstructions(E element, ArtifactCompilerInstructionCreator instructionCreator, ArtifactInstructionsBuilderContext builderContext);

  public Collection<? extends BuildTarget<?>> getDependencies(@NotNull E element, TargetOutputIndex outputIndex) {
    return Collections.emptyList();
  }

  public final Class<E> getElementClass() {
    return myElementClass;
  }
}
