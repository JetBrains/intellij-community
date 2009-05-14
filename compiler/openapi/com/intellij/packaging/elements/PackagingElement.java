package com.intellij.packaging.elements;

import com.intellij.compiler.ant.Generator;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.packaging.ui.PackagingEditorContext;
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

  public abstract PackagingElementPresentation createPresentation(PackagingEditorContext context);

  public final PackagingElementType getType() {
    return myType;
  }

  public abstract List<? extends Generator> computeAntInstructions(@NotNull PackagingElementResolvingContext resolvingContext,
                                                                    @NotNull AntCopyInstructionCreator creator, 
                                                                    @NotNull ArtifactAntGenerationContext generationContext);

  public abstract void computeIncrementalCompilerInstructions(@NotNull IncrementalCompilerInstructionCreator creator, 
                                                              @NotNull PackagingElementResolvingContext resolvingContext,
                                                              @NotNull ArtifactIncrementalCompilerContext compilerContext);

  public abstract boolean isEqualTo(@NotNull PackagingElement<?> element);
}
