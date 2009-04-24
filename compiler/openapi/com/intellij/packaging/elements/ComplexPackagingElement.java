package com.intellij.packaging.elements;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public abstract class ComplexPackagingElement<S> extends PackagingElement<S> {
  protected ComplexPackagingElement(PackagingElementType type) {
    super(type);
  }

  @Nullable
  public abstract List<? extends PackagingElement<?>> getSubstitution(@NotNull PackagingElementResolvingContext context);

}
