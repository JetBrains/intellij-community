// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;

/**
 * An object allowing to decouple {@link LookupElement#handleInsert} logic from the lookup element class, e.g. for the purposes
 * of overriding its behavior or reusing the logic between multiple types of elements.
 * @see com.intellij.codeInsight.lookup.LookupElementDecorator#withInsertHandler
 * @see com.intellij.codeInsight.completion.util.ParenthesesInsertHandler
 */
public interface InsertHandler<T extends LookupElement> {

  /**
   * Invoked inside write action.
   */
  void handleInsert(@NotNull InsertionContext context, @NotNull T item);
}
