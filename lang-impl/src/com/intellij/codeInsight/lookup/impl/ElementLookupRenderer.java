package com.intellij.codeInsight.lookup.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.codeInsight.lookup.LookupItem;

/**
 * @author yole
 */
public interface ElementLookupRenderer<T> {
  ExtensionPointName<ElementLookupRenderer> EP_NAME = ExtensionPointName.create("com.intellij.elementLookupRenderer");

  boolean handlesItem(Object element);
  void renderElement(final LookupItem item, T element, LookupElementPresentation presentation);
}
