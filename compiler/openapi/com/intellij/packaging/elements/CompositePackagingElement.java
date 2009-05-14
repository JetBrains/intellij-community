package com.intellij.packaging.elements;

import com.intellij.compiler.ant.Generator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public abstract class CompositePackagingElement<S> extends PackagingElement<S> {
  private final List<PackagingElement<?>> myChildren = new ArrayList<PackagingElement<?>>();

  protected CompositePackagingElement(PackagingElementType type) {
    super(type);
  }

  public void addChild(@NotNull PackagingElement<?> child) {
    myChildren.add(child);
  }

  public void addChildren(Collection<? extends PackagingElement<?>> children) {
    myChildren.addAll(children);
  }

  public void removeChild(@NotNull PackagingElement<?> child) {
    myChildren.remove(child);
  }

  public List<PackagingElement<?>> getChildren() {
    return myChildren;
  }

  public abstract String getName();

  public boolean canBeRenamed() {
    return true;
  }

  public abstract void rename(@NotNull String newName);

  protected List<? extends Generator> computeChildrenGenerators(PackagingElementResolvingContext resolvingContext,
                                                                final AntCopyInstructionCreator copyInstructionCreator,
                                                                final ArtifactAntGenerationContext generationContext) {
    final List<Generator> generators = new ArrayList<Generator>();
    for (PackagingElement<?> child : myChildren) {
      generators.addAll(child.computeAntInstructions(resolvingContext, copyInstructionCreator, generationContext));
    }
    return generators;
  }

  protected void computeChildrenInstructions(@NotNull IncrementalCompilerInstructionCreator creator,
                                             @NotNull PackagingElementResolvingContext resolvingContext,
                                             @NotNull ArtifactIncrementalCompilerContext compilerContext) {
    for (PackagingElement<?> child : myChildren) {
      child.computeIncrementalCompilerInstructions(creator, resolvingContext, compilerContext);
    }
  }
}
