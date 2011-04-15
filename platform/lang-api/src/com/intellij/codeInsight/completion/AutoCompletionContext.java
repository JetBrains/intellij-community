/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;

/**
 * @author peter
 */
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
