// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class AutoCompletionDecision {
  public static final AutoCompletionDecision SHOW_LOOKUP = new AutoCompletionDecision();
  public static final AutoCompletionDecision CLOSE_LOOKUP = new AutoCompletionDecision();

  public static AutoCompletionDecision insertItem(@NotNull LookupElement element) {
    return new InsertItem(element);
  }

  private AutoCompletionDecision() {
  }

  static final class InsertItem extends AutoCompletionDecision {
    private final LookupElement myElement;

    private InsertItem(LookupElement element) {
      myElement = element;
    }

    public LookupElement getElement() {
      return myElement;
    }
  }

}
