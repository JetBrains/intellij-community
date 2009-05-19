package com.intellij.packaging.elements;

import com.intellij.compiler.ant.Generator;
import com.intellij.packaging.artifacts.ArtifactType;
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
  public List<? extends Generator> computeAntInstructions(@NotNull PackagingElementResolvingContext resolvingContext, @NotNull AntCopyInstructionCreator creator,
                                                          @NotNull ArtifactAntGenerationContext generationContext,
                                                          @NotNull ArtifactType artifactType) {
    final List<? extends PackagingElement<?>> substitution = getSubstitution(resolvingContext, artifactType);
    if (substitution == null) {
      return Collections.emptyList();
    }

    final List<Generator> fileSets = new ArrayList<Generator>();
    for (PackagingElement<?> element : substitution) {
      fileSets.addAll(element.computeAntInstructions(resolvingContext, creator, generationContext, artifactType));
    }
    return fileSets;
  }

  @Override
  public void computeIncrementalCompilerInstructions(@NotNull IncrementalCompilerInstructionCreator creator,
                                                     @NotNull PackagingElementResolvingContext resolvingContext,
                                                     @NotNull ArtifactIncrementalCompilerContext compilerContext, @NotNull ArtifactType artifactType) {
    final List<? extends PackagingElement<?>> substitution = getSubstitution(resolvingContext, artifactType);
    if (substitution == null) return;

    for (PackagingElement<?> element : substitution) {
      element.computeIncrementalCompilerInstructions(creator, resolvingContext, compilerContext,
                                                     getArtifactTypeForSubstitutedElements(resolvingContext, artifactType));
    }
  }

  protected ArtifactType getArtifactTypeForSubstitutedElements(PackagingElementResolvingContext resolvingContext, ArtifactType artifactType) {
    return artifactType;
  }


  @Nullable
  public abstract List<? extends PackagingElement<?>> getSubstitution(@NotNull PackagingElementResolvingContext context, @NotNull ArtifactType artifactType);

}
