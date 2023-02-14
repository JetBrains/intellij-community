// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;

public class AutoCompletionContext {
  private final CompletionParameters myParameters;
  private final LookupElement[] myItems;
  private final OffsetMap myOffsetMap;
  private final Lookup myLookup;

  public AutoCompletionContext(CompletionParameters parameters, LookupElement[] items, OffsetMap offsetMap, Lookup lookup) {
    myParameters = parameters;
    myItems = items;
    myOffsetMap = offsetMap;
    myLookup = lookup;
  }

  public Lookup getLookup() {
    return myLookup;
  }

  public CompletionParameters getParameters() {
    return myParameters;
  }

  public LookupElement[] getItems() {
    return myItems;
  }

  public OffsetMap getOffsetMap() {
    return myOffsetMap;
  }

}
