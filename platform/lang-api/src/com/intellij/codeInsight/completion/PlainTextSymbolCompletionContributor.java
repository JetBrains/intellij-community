// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * A language-specific completion contributor which provides reasonable amount of symbols declared in given file
 * (likely only top-level declarations). Such contributor could be used in plain text editors like VCS commit message field
 * to help users referring to code symbols from the text.
 *
 * @see PlainTextSymbolCompletionContributorEP
 */
public interface PlainTextSymbolCompletionContributor {
  /**
   * Adds lookup elements from given file.
   *
   * @param file file to add elements from.
   * @param invocationCount number of times the completion was invoked (see {@link CompletionParameters#getInvocationCount()}).
   * @param prefix a prefix string. It's not required to return only matches starting with prefix, but this may be used to improve
   *               performance.
   * @return a collection of {@link LookupElement}'s to suggest.
   */
  @NotNull
  Collection<LookupElement> getLookupElements(@NotNull PsiFile file, int invocationCount, @NotNull String prefix);
}
