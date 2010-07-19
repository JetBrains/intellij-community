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
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class CompletionServiceImpl extends CompletionService{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.impl.CompletionServiceImpl");
  private Throwable myTrace = null;
  private CompletionProgressIndicator myCurrentCompletion;

  public static CompletionServiceImpl getCompletionService() {
    return (CompletionServiceImpl)CompletionService.getCompletionService();
  }

  @Override
  public String getAdvertisementText() {
    final CompletionProgressIndicator completion = getCompletionService().getCurrentCompletion();
    return completion == null ? null : completion.getLookup().getAdvertisementText();
  }

  public void setAdvertisementText(@Nullable final String text) {
    final CompletionProgressIndicator completion = getCompletionService().getCurrentCompletion();
    if (completion != null) {
      completion.getLookup().setAdvertisementText(text);
    }
  }

  public CompletionResultSet createResultSet(final CompletionParameters parameters, final Consumer<LookupElement> consumer,
                                                      @NotNull final CompletionContributor contributor) {
    return ApplicationManager.getApplication().runReadAction(new Computable<CompletionResultSet>() {
      public CompletionResultSet compute() {
        final PsiElement position = parameters.getPosition();
        final String prefix = CompletionData.findPrefixStatic(position, parameters.getOffset());

        final String textBeforePosition = parameters.getPosition().getContainingFile().getText().substring(0, parameters.getOffset());

        return new CompletionResultSetImpl(consumer, textBeforePosition, new CamelHumpMatcher(prefix), contributor);
      }
    });
  }

  @Override
  public CompletionProgressIndicator getCurrentCompletion() {
    return myCurrentCompletion;
  }

  public void setCurrentCompletion(@Nullable final CompletionProgressIndicator indicator) {
    if (indicator != null) {
      final CompletionProgressIndicator oldCompletion = myCurrentCompletion;
      final Throwable oldTrace = myTrace;
      myCurrentCompletion = indicator;
      myTrace = new Throwable();
      if (oldCompletion != null) {
        throw new RuntimeException(
          "SHe's not dead yet!\nthis=" + indicator + "\ncurrent=" + oldCompletion + "\ntrace=" + StringUtil.getThrowableText(oldTrace));
      }
    } else {
      myCurrentCompletion = null;
    }
  }

  private static class CompletionResultSetImpl extends CompletionResultSet {
    private final String myTextBeforePosition;

    public CompletionResultSetImpl(final Consumer<LookupElement> consumer, final String textBeforePosition,
                                   final PrefixMatcher prefixMatcher, CompletionContributor contributor) {
      super(prefixMatcher, consumer, contributor);
      myTextBeforePosition = textBeforePosition;
    }

    @NotNull
    public CompletionResultSet withPrefixMatcher(@NotNull final PrefixMatcher matcher) {
      if (!myTextBeforePosition.endsWith(matcher.getPrefix())) {
        final int len = myTextBeforePosition.length();
        final String fragment = len > 100 ? myTextBeforePosition.substring(len - 100) : myTextBeforePosition;
        LOG.error("prefix should be some actual file string just before caret: " + matcher.getPrefix() + "\n text=" + fragment);
      }
      return new CompletionResultSetImpl(getConsumer(), myTextBeforePosition, matcher, myContributor) {
        @Override
        public void stopHere() {
          super.stopHere();
          CompletionResultSetImpl.this.stopHere();
        }
      };
    }

    @NotNull
    public CompletionResultSet withPrefixMatcher(@NotNull final String prefix) {
      return withPrefixMatcher(new CamelHumpMatcher(prefix));
    }

    @NotNull
    @Override
    public CompletionResultSet caseInsensitive() {
      return withPrefixMatcher(new CamelHumpMatcher(getPrefixMatcher().getPrefix(), false));
    }
  }

  public void correctCaseInsensitiveString(@NotNull final LookupElement element, InsertionContext context) {
    if (!element.isPrefixMatched()) {
      return;
    }

    final String prefix = element.getPrefixMatcher().getPrefix();
    final String oldLookupString = element.getLookupString();
    if (StringUtil.startsWithIgnoreCase(oldLookupString, prefix)) {
      final String newLookupString = handleCaseInsensitiveVariant(prefix, oldLookupString);
      if (!newLookupString.equals(oldLookupString)) {
        final Document document = context.getEditor().getDocument();
        document.replaceString(context.getStartOffset(), context.getTailOffset(), newLookupString);
        PsiDocumentManager.getInstance(context.getProject()).commitDocument(document);
      }
    }
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
