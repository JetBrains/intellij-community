/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.lookup.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;

/**
 * @author peter
 */
public class CompletionPreview implements Disposable {
  private static final Key<CompletionPreview> COMPLETION_PREVIEW_KEY = Key.create("COMPLETION_PREVIEW_KEY");
  private final LookupImpl myLookup;

  private CompletionPreview(LookupImpl lookup) {
    myLookup = lookup;
    myLookup.putUserData(COMPLETION_PREVIEW_KEY, this);

    Disposer.register(myLookup, this);
  }

  public static boolean hasPreview(LookupImpl lookup) {
    return COMPLETION_PREVIEW_KEY.get(lookup) != null;
  }

  @Override
  public void dispose() {
  }

  public static void installPreview(LookupImpl lookup) {
    new CompletionPreview(lookup);
  }

}
