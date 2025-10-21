// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.completion;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.group.GroupedCompletionContributor;
import com.intellij.codeInsight.template.CustomLiveTemplate;
import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings;
import com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate;
import com.intellij.openapi.editor.Editor;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class PostfixTemplateCompletionContributor extends CompletionContributor implements GroupedCompletionContributor {
  public PostfixTemplateCompletionContributor() {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new PostfixTemplatesCompletionProvider());
  }

  @Override
  public boolean groupIsEnabled(CompletionParameters parameters) {
    PostfixTemplatesSettings settings = PostfixTemplatesSettings.getInstance();
    return settings.isPostfixTemplatesEnabled() && settings.isShowAsSeparateGroup();
  }

  @Override
  public @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getGroupDisplayName() {
    return CodeInsightBundle.message("command.completion.title");
  }

  public static @Nullable PostfixLiveTemplate getPostfixLiveTemplate(@NotNull PsiFile file, @NotNull Editor editor) {
    PostfixLiveTemplate postfixLiveTemplate = CustomLiveTemplate.EP_NAME.findExtension(PostfixLiveTemplate.class);
    TemplateActionContext templateActionContext = TemplateActionContext.expanding(file, editor);
    return postfixLiveTemplate != null && TemplateManagerImpl.isApplicable(postfixLiveTemplate, templateActionContext) ?
           postfixLiveTemplate : null;
  }
}
