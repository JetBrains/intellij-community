// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.completion;

import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.impl.CustomLiveTemplateLookupElement;
import com.intellij.codeInsight.template.postfix.templates.CustomizableLookupElementTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class PostfixTemplateLookupElement extends CustomLiveTemplateLookupElement {
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
  public Set<String> getAllLookupStrings() {
    Set<String> allLookupStrings = super.getAllLookupStrings();
    if (myTemplate instanceof CustomizableLookupElementTemplate customizableRenderPresentation) {
      allLookupStrings = new HashSet<>(allLookupStrings);
      allLookupStrings.addAll(customizableRenderPresentation.getAllLookupStrings());
    }
    return allLookupStrings;
  }

  @Override
  public void renderElement(@NotNull LookupElementPresentation presentation) {
    super.renderElement(presentation);
    presentation.setTypeText(myTemplate.getExample());
    presentation.setTypeGrayed(true);
    if (myTemplate instanceof CustomizableLookupElementTemplate customizableRenderPresentation) {
      customizableRenderPresentation.renderElement(presentation);
    }
  }

  @Override
  public void expandTemplate(@NotNull Editor editor, @NotNull PsiFile file) {
    PostfixLiveTemplate.expandTemplate(myTemplateKey, new CustomTemplateCallback(editor, file), editor, myProvider, myTemplate);
  }
}
