// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.completion;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInsight.lookup.LookupElementCustomPreviewHolder;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.impl.CustomLiveTemplateLookupElement;
import com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixModExpander;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatesUtils;
import com.intellij.modcommand.ActionContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class PostfixTemplateLookupElement extends CustomLiveTemplateLookupElement implements LookupElementCustomPreviewHolder {
  private final @NotNull PostfixTemplate myTemplate;
  private final @NotNull String myTemplateKey;
  private final @NotNull PostfixTemplateProvider myProvider;


  public PostfixTemplateLookupElement(@NotNull PostfixLiveTemplate liveTemplate,
                                      @NotNull PostfixTemplate postfixTemplate,
                                      @NotNull String templateKey,
                                      @NotNull PostfixTemplateProvider provider,
                                      boolean sudden) {
    super(liveTemplate, templateKey, StringUtil.trimStart(templateKey, "."), postfixTemplate.getDescription(), sudden, true);
    myTemplate = postfixTemplate;
    myTemplateKey = templateKey;
    myProvider = provider;
  }

  public @NotNull PostfixTemplate getPostfixTemplate() {
    return myTemplate;
  }

  public @NotNull PostfixTemplateProvider getProvider() {
    return myProvider;
  }

  @Override
  public void renderElement(@NotNull LookupElementPresentation presentation) {
    super.renderElement(presentation);
    presentation.setTypeText(myTemplate.getExample());
    presentation.setTypeGrayed(true);
  }

  @Override
  public void expandTemplate(@NotNull Editor editor, @NotNull PsiFile file) {
    PostfixLiveTemplate.expandTemplate(myTemplateKey, new CustomTemplateCallback(editor, file), editor, myProvider, myTemplate);
  }

  @ApiStatus.Experimental
  @Override
  public @NotNull IntentionPreviewInfo preview(@NotNull ActionContext ctx) {
    PostfixModExpander expander = myTemplate.createModExpander();
    if (myTemplate.isApplicableForModCommand() && expander != null) {
      String key = PostfixLiveTemplate.computeTemplateKeyWithoutContextChecking(
        myProvider, ctx.file().getFileDocument().getCharsSequence(), ctx.offset());
      if (key == null) return IntentionPreviewInfo.EMPTY;
      TextRange keyRange = PostfixTemplatesUtils.computeKeyRange(ctx, key, myTemplate.getKey());
      var command = expander.expand(ctx, myProvider, keyRange);
      return IntentionPreviewUtils.getModCommandPreview(command, ctx);
    }
    return IntentionPreviewInfo.EMPTY;
  }
}
