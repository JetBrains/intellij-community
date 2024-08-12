// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.settings;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.impl.config.BeforeAfterMetaData;
import com.intellij.codeInsight.intention.impl.config.PlainTextDescriptor;
import com.intellij.codeInsight.intention.impl.config.TextDescriptor;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.postfix.templates.editable.EditablePostfixTemplate;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class EditablePostfixTemplateMetaData implements BeforeAfterMetaData {

  private static final @NlsSafe String EXPR = "$EXPR$";
  private static final @NlsSafe String END = "$END$";

  private final @NotNull @Nls String myAfterText;
  private final @NotNull @Nls String myBeforeText;

  public EditablePostfixTemplateMetaData(@NotNull EditablePostfixTemplate template) {
    TemplateImpl liveTemplate = template.getLiveTemplate();
    String text = liveTemplate.getString();

    myBeforeText = HtmlChunk.tag("spot").addText(EXPR).toString() + template.getKey();
    myAfterText = StringUtil.replace(text, END, HtmlChunk.tag("spot").toString(), true);
  }

  @Override
  public TextDescriptor @NotNull [] getExampleUsagesBefore() {
    return new TextDescriptor[]{new PlainTextDescriptor(myBeforeText, "before.txt")};
  }

  @Override
  public TextDescriptor @NotNull [] getExampleUsagesAfter() {
    return new TextDescriptor[]{new PlainTextDescriptor(myAfterText, "after.txt")};
  }

  @Override
  public @NotNull TextDescriptor getDescription() {
    return new PlainTextDescriptor(CodeInsightBundle.message("templates.postfix.editable.description"), "description.txt");
  }
}
