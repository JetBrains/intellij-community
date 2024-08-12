// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.template.CustomLiveTemplate;
import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate;
import com.intellij.openapi.editor.Editor;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PostfixTemplateCompletionContributor extends CompletionContributor {
  public PostfixTemplateCompletionContributor() {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new PostfixTemplatesCompletionProvider());
  }

  public static @Nullable PostfixLiveTemplate getPostfixLiveTemplate(@NotNull PsiFile file, @NotNull Editor editor) {
    PostfixLiveTemplate postfixLiveTemplate = CustomLiveTemplate.EP_NAME.findExtension(PostfixLiveTemplate.class);
    TemplateActionContext templateActionContext = TemplateActionContext.expanding(file, editor);
    return postfixLiveTemplate != null && TemplateManagerImpl.isApplicable(postfixLiveTemplate, templateActionContext) ?
           postfixLiveTemplate : null;
  }
}
