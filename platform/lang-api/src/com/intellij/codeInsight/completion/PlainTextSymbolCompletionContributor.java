// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * A language-specific completion contributor that should provide a reasonable number of symbols declared in a given file, e.g.,
 * only top-level declarations.
 * Such contributors can also help to complete words in plain text editors like VCS commit messages.
 *
 * Please see the <a href="https://plugins.jetbrains.com/docs/intellij/code-completion.html#contributor-based-completion">IntelliJ Platform Docs</a>
 * for a high-level overview of contributor-based completion.
 *
 * @see PlainTextSymbolCompletionContributorEP
 */
public interface PlainTextSymbolCompletionContributor {
  /**
   * Collects lookup elements from a given file.
   *
   * @param file file for extracting lookup elements.
   * @param invocationCount number of times the completion was invoked (see {@link CompletionParameters#getInvocationCount()}).
   * @param prefix prefix string that can be used to filter candidates.
   *              It's not required to return only matches starting with prefix, but it can improve performance.
   * @return collection of suggestions used for completion.
   */
  @NotNull
  @Unmodifiable
  Collection<LookupElement> getLookupElements(@NotNull PsiFile file, int invocationCount, @NotNull String prefix);
}
