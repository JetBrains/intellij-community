package com.intellij.packaging.elements;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class ArtifactRootElement<S> extends CompositePackagingElement<S> {
  protected ArtifactRootElement(PackagingElementType type) {
    super(type);
  }

  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    return false;
  }
}
