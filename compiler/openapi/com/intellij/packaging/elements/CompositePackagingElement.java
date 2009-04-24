package com.intellij.packaging.elements;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;

/**
 * @author nik
 */
public abstract class CompositePackagingElement<S> extends PackagingElement<S> {
  private final List<PackagingElement<?>> myChildren = new ArrayList<PackagingElement<?>>();

  protected CompositePackagingElement(PackagingElementType type) {
    super(type);
  }

  public void addChild(PackagingElement<?> child) {
    myChildren.add(child);
  }

  public void removeChild(PackagingElement<?> child) {
    myChildren.remove(child);
  }

  public List<PackagingElement<?>> getChildren() {
    return myChildren;
  }

  public boolean canBeMergedWith(@NotNull PackagingElement<?> element) {
    return false;
  }
}
