package com.intellij.packaging.elements;

import com.intellij.compiler.ant.Generator;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public abstract class PackagingElement<S> implements PersistentStateComponent<S> {
  private final PackagingElementType myType;

  protected PackagingElement(PackagingElementType type) {
    myType = type;
  }

  public abstract PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context);

  public final PackagingElementType getType() {
    return myType;
  }

  public abstract List<? extends Generator> computeAntInstructions(@NotNull PackagingElementResolvingContext resolvingContext, @NotNull AntCopyInstructionCreator creator,
                                                                   @NotNull ArtifactAntGenerationContext generationContext,
                                                                   @NotNull ArtifactType artifactType);

  public abstract void computeIncrementalCompilerInstructions(@NotNull IncrementalCompilerInstructionCreator creator, @NotNull PackagingElementResolvingContext resolvingContext,
                                                              @NotNull ArtifactIncrementalCompilerContext compilerContext,
                                                              @NotNull ArtifactType artifactType);

  public abstract boolean isEqualTo(@NotNull PackagingElement<?> element);

  @NotNull
  public PackagingElementOutputKind getFilesKind(PackagingElementResolvingContext context) {
    return PackagingElementOutputKind.OTHER;
  }
}
