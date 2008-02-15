package com.intellij.codeInsight.lookup.impl;

import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * @author yole
 */
public interface ElementLookupRenderer<T> {
  ExtensionPointName<ElementLookupRenderer> EP_NAME = ExtensionPointName.create("com.intellij.elementLookupRenderer");

  boolean handlesItem(Object element);
  void renderElement(T element, LookupElementPresentation presentation);
}
