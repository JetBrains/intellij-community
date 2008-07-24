package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * @author yole
 */
public interface ElementLookupRenderer<T> {
  ExtensionPointName<ElementLookupRenderer> EP_NAME = ExtensionPointName.create("com.intellij.elementLookupRenderer");

  boolean handlesItem(Object element);
  void renderElement(final LookupItem item, T element, LookupElementPresentationEx presentation);

}
