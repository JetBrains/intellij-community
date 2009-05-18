package com.intellij.packaging.elements;

import com.intellij.compiler.ant.Generator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class CompositePackagingElement<S> extends PackagingElement<S> {
  private final List<PackagingElement<?>> myChildren = new ArrayList<PackagingElement<?>>();
  private List<PackagingElement<?>> myUnmodifiableChildren;

  protected CompositePackagingElement(PackagingElementType type) {
    super(type);
  }

  public <T extends PackagingElement<?>> T addOrFindChild(@NotNull T child) {
    for (PackagingElement<?> element : myChildren) {
      if (element.isEqualTo(child)) {
        if (element instanceof CompositePackagingElement) {
          final List<PackagingElement<?>> children = ((CompositePackagingElement<?>)child).getChildren();
          ((CompositePackagingElement<?>)element).addOrFindChildren(children);
        }
        //noinspection unchecked
        return (T) element;
      }
    }
    myChildren.add(child);
    myUnmodifiableChildren = null;
    return child;
  }

  public List<? extends PackagingElement<?>> addOrFindChildren(Collection<? extends PackagingElement<?>> children) {
    List<PackagingElement<?>> added = new ArrayList<PackagingElement<?>>();
    for (PackagingElement<?> child : children) {
      added.add(addOrFindChild(child));
    }
    myUnmodifiableChildren = null;
    return added;
  }

  public void removeChild(@NotNull PackagingElement<?> child) {
    myChildren.remove(child);
    myUnmodifiableChildren = null;
  }

  @NotNull
  public List<PackagingElement<?>> getChildren() {
    if (myUnmodifiableChildren == null) {
      myUnmodifiableChildren = Collections.unmodifiableList(myChildren);
    }
    return myUnmodifiableChildren;
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
