// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.template.CustomLiveTemplateBase;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CustomLiveTemplateLookupElement extends LiveTemplateLookupElement {
  private final @NotNull CustomLiveTemplateBase myCustomLiveTemplate;

  private final @NotNull String myTemplateKey;
  private final @NotNull String myItemText;

  public CustomLiveTemplateLookupElement(@NotNull CustomLiveTemplateBase customLiveTemplate,
                                         @NotNull @NlsSafe String templateKey,
                                         @NotNull @NlsSafe String itemText,
                                         @Nullable @NlsContexts.DetailedDescription String description,
                                         boolean sudden,
                                         boolean worthShowingInAutoPopup) {
    super(templateKey, description, sudden, worthShowingInAutoPopup);
    myCustomLiveTemplate = customLiveTemplate;
    myTemplateKey = templateKey;
    myItemText = itemText;
  }

  @Override
  protected @NotNull String getItemText() {
    return myItemText;
  }

  public @NotNull CustomLiveTemplateBase getCustomLiveTemplate() {
    return myCustomLiveTemplate;
  }

  @Override
  public char getTemplateShortcut() {
    return myCustomLiveTemplate.getShortcut();
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context) {
    context.setAddCompletionChar(false);
    expandTemplate(context.getEditor(), context.getFile());
  }

  public void expandTemplate(@NotNull Editor editor, @NotNull PsiFile file) {
    myCustomLiveTemplate.expand(myTemplateKey, new CustomTemplateCallback(editor, file));
  }
}
