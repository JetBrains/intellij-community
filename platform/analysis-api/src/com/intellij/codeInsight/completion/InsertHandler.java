// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mike
 */
public interface InsertHandler<T extends LookupElement> {

  /**
   * Invoked inside atomic action.
   */
  void handleInsert(@NotNull InsertionContext context, @NotNull T item);
}
