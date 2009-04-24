package com.intellij.packaging.elements;

/**
 * @author nik
 */
public abstract class ArtifactRootElement<S> extends CompositePackagingElement<S> {
  protected ArtifactRootElement(PackagingElementType type) {
    super(type);
  }
}
