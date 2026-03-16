// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.completion;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.command.CommandCompletionFactory;
import com.intellij.codeInsight.completion.command.CommandCompletionService;
import com.intellij.codeInsight.completion.command.InvocationCommandType;
import com.intellij.codeInsight.completion.command.configuration.CommandCompletionSettingsService;
import com.intellij.codeInsight.completion.group.GroupedCompletionContributor;
import com.intellij.codeInsight.template.CustomLiveTemplate;
import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings;
import com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.completion.command.CommandCompletionProviderKt.findCommandCompletionType;

@ApiStatus.Internal
public final class PostfixTemplateCompletionContributor extends CompletionContributor implements GroupedCompletionContributor {
  public PostfixTemplateCompletionContributor() {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new PostfixTemplatesCompletionProvider());
  }

  @Override
  public boolean groupIsEnabled(CompletionParameters parameters) {
    PostfixTemplatesSettings settings = PostfixTemplatesSettings.getInstance();
    boolean isPostfixTemplatesVisible = settings.isPostfixTemplatesEnabled() && settings.isShowAsSeparateGroup();
    if (!isPostfixTemplatesVisible) return false;
    if (!CommandCompletionSettingsService.getInstance().commandCompletionEnabled()) return false;
    Editor editor = parameters.getEditor();
    Project project = editor.getProject();
    if (project == null) return false;
    CommandCompletionService commandCompletionService = project.getService(CommandCompletionService.class);
    if (commandCompletionService == null) return false;
    CommandCompletionFactory factory = commandCompletionService.getFactory(parameters.getOriginalFile().getLanguage());
    if (factory == null) return false;
    boolean supportFiltersWithDoublePrefix = factory.supportFiltersWithDoublePrefix();
    InvocationCommandType commandType = findCommandCompletionType(factory,
                                                                  !parameters.getOriginalFile().isWritable(),
                                                                  parameters.getEditor().getCaretModel().getOffset(),
                                                                  parameters.getEditor());
    if (commandType instanceof InvocationCommandType.FullSuffix && supportFiltersWithDoublePrefix) return false;
    return true;
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
