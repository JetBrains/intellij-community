// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.template.impl.CustomLiveTemplateLookupElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public abstract class CustomLiveTemplateBase implements CustomLiveTemplate {
  /**
   * Implementation should returns {@code true} if it has own lookup item in completion autopopup
   * and it is supposed that template should be expanded while completion auto-popup is active.
   */
  public boolean hasCompletionItem(@NotNull CustomTemplateCallback callback, int offset) {
    return false;
  }

  /**
   * Return lookup elements for popup that appears on ListTemplateAction (Ctrl + J)
   */
  public @NotNull Collection<? extends CustomLiveTemplateLookupElement> getLookupElements(@NotNull PsiFile file, @NotNull Editor editor, int offset) {
    return Collections.emptyList();
  }

  /**
   * Populate completion result set. Used by LiveTemplateCompletionContributor
   */
  public void addCompletions(CompletionParameters parameters, CompletionResultSet result) {
    String prefix = computeTemplateKeyWithoutContextChecking(new CustomTemplateCallback(parameters.getEditor(), parameters.getOriginalFile()));
    if (prefix != null) {
      result.withPrefixMatcher(result.getPrefixMatcher().cloneWithPrefix(prefix)).addAllElements(
        getLookupElements(parameters.getOriginalFile(), parameters.getEditor(), parameters.getOffset()));
    }
  }

  public @Nullable String computeTemplateKeyWithoutContextChecking(@NotNull CustomTemplateCallback callback) {
    return computeTemplateKey(callback);
  }

  public boolean supportsMultiCaret() {
    return true;
  }
}
