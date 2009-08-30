package com.intellij.packaging.impl.elements;

import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementType;

/**
 * @author nik
 */
public abstract class CompositeElementWithManifest<T> extends CompositePackagingElement<T> {
  protected CompositeElementWithManifest(PackagingElementType type) {
    super(type);
  }
}
