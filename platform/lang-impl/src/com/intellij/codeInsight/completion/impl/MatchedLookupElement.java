/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.ClassConditionKey;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class MatchedLookupElement extends LookupElementDecorator<LookupElement> {
  public static final ClassConditionKey<MatchedLookupElement> CLASS_CONDITION_KEY = ClassConditionKey.create(MatchedLookupElement.class);
  private final PrefixMatcher myMatcher;
  private final CompletionSorterImpl mySorter;

  MatchedLookupElement(LookupElement delegate, PrefixMatcher matcher, CompletionSorterImpl sorter) {
    super(delegate);
    myMatcher = matcher;
    mySorter = sorter;
  }

  public PrefixMatcher getMatcher() {
    return myMatcher;
  }

  public CompletionSorterImpl getSorter() {
    return mySorter;
  }

  @Override
  public void handleInsert(InsertionContext context) {
    if (!isCaseSensitive()) {
      if (!myMatcher.prefixMatches(this)) {
        return;
      }

      final String prefix = myMatcher.getPrefix();
      final String oldLookupString = getLookupString();
      if (StringUtil.startsWithIgnoreCase(oldLookupString, prefix)) {
        final String newLookupString = handleCaseInsensitiveVariant(prefix, oldLookupString);
        if (!newLookupString.equals(oldLookupString)) {
          final Document document = context.getEditor().getDocument();
          int startOffset = context.getStartOffset();
          int tailOffset = context.getTailOffset();

          assert startOffset >= 0 : "stale startOffset";
          assert tailOffset >= 0 : "stale tailOffset";

          document.replaceString(startOffset, tailOffset, newLookupString);
          PsiDocumentManager.getInstance(context.getProject()).commitDocument(document);
        }
      }

    }
    super.handleInsert(context);
  }

  private static String handleCaseInsensitiveVariant(final String prefix, @NotNull final String lookupString) {
    final int length = prefix.length();
    if (length == 0) return lookupString;
    boolean isAllLower = true;
    boolean isAllUpper = true;
    boolean sameCase = true;
    for (int i = 0; i < length && (isAllLower || isAllUpper || sameCase); i++) {
      final char c = prefix.charAt(i);
      isAllLower = isAllLower && Character.isLowerCase(c);
      isAllUpper = isAllUpper && Character.isUpperCase(c);
      sameCase = sameCase && Character.isLowerCase(c) == Character.isLowerCase(lookupString.charAt(i));
    }
    if (sameCase) return lookupString;
    if (isAllLower) return lookupString.toLowerCase();
    if (isAllUpper) return lookupString.toUpperCase();
    return lookupString;
  }

}
