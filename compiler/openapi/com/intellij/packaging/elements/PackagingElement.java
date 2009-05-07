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

  public abstract List<? extends Generator> computeCopyInstructions(@NotNull PackagingElementResolvingContext resolvingContext,
                                                            @NotNull CopyInstructionCreator creator, @NotNull ArtifactGenerationContext generationContext);

  public abstract boolean isEqualTo(@NotNull PackagingElement<?> element);
}
