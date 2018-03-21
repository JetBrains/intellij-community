// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.util.Pair;

import java.util.List;

/**
 * Determines the order of completion items and the initial selection.
 *
 * @author yole
 */
public interface CompletionLookupArranger {
  /**
   * Adds an element to be arranged.
   * @param presentation The presentation of the element (rendered with {@link LookupElement#renderElement(LookupElementPresentation)}
   */
  void addElement(LookupElement element, LookupElementPresentation presentation);

  /**
   * Returns the items in the appropriate order and the initial selection.
   *
   * @return Pair where the first element is the sorted list of completion items and the second item is the index of the item to select
   *         initially.
   */
  Pair<List<LookupElement>, Integer> arrangeItems();
}
