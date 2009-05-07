package com.intellij.packaging.elements;

import com.intellij.compiler.ant.Generator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * @author nik
 */
public abstract class ComplexPackagingElement<S> extends PackagingElement<S> {
  protected ComplexPackagingElement(PackagingElementType type) {
    super(type);
  }

  @Override
  public List<? extends Generator> computeCopyInstructions(@NotNull PackagingElementResolvingContext resolvingContext, @NotNull CopyInstructionCreator creator,
                                                   @NotNull ArtifactGenerationContext generationContext) {
    final List<? extends PackagingElement<?>> substitution = getSubstitution(resolvingContext);
    if (substitution == null) {
      return Collections.emptyList();
    }

    final List<Generator> fileSets = new ArrayList<Generator>();
    for (PackagingElement<?> element : substitution) {
      fileSets.addAll(element.computeCopyInstructions(resolvingContext, creator, generationContext));
    }
    return fileSets;
  }

  @Nullable
  public abstract List<? extends PackagingElement<?>> getSubstitution(@NotNull PackagingElementResolvingContext context);

}
