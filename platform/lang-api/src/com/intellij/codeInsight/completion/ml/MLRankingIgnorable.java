// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.ml;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A marker interface for the {@link LookupElement} inheritors to ignore this element
 * in sorting by Machine Learning-assisted completion.
 */
@ApiStatus.Experimental
public interface MLRankingIgnorable {
  static LookupElement wrap(LookupElement element) {
    class MLIgnorableLookupElement extends LookupElementDecorator<LookupElement> implements MLRankingIgnorable {

      private MLIgnorableLookupElement(@NotNull LookupElement delegate) {
        super(delegate);
      }
    }

    return new MLIgnorableLookupElement(element);
  }
}
