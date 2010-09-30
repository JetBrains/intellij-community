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

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public class PreferSimpleWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement item, @Nullable final CompletionLocation location) {
    if (location == null) {
      return null;
    }
    final PsiTypeLookupItem lookupItem = PsiTypeLookupItem.from(item);
    if (lookupItem != null) {
      return -lookupItem.getBracketsCount();
    }
    if (CastingLookupElementDecorator.from(item) != null) {
      return -239;
    }
    return 0;
  }
}