// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * @deprecated use {@link com.intellij.codeInsight.lookup.LookupElement#renderElement(LookupElementPresentation)}
 */
@Deprecated(forRemoval = true)
public interface ElementLookupRenderer<T> {
  ExtensionPointName<ElementLookupRenderer> EP_NAME = ExtensionPointName.create("com.intellij.elementLookupRenderer");

  boolean handlesItem(Object element);
  void renderElement(final LookupItem item, T element, LookupElementPresentation presentation);

}
