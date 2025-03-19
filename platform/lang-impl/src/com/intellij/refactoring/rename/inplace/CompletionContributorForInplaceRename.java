// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.inplace;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class CompletionContributorForInplaceRename extends CompletionContributor {

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    final Editor editor = parameters.getEditor();
    final TemplateState state = TemplateManagerImpl.getTemplateState(editor);
    if (state != null) {
      if (editor.getUserData(InplaceRefactoring.INPLACE_RENAMER) != null && parameters.getInvocationCount() == 0) {
        result.stopHere();
      }
    }
  }
}
