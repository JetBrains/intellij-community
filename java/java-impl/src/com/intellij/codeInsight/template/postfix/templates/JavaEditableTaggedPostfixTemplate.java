// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaEditablePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateExpressionCondition;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@SuppressWarnings("PostfixTemplateDescriptionNotFound")
public class JavaEditableTaggedPostfixTemplate extends JavaEditablePostfixTemplate implements CustomizableLookupElementTemplate {

  @SuppressWarnings("RegExpUnexpectedAnchor") private static final String EXPR_$ = "$EXPR$";

  @Nullable
  private String myText;

  @NotNull
  private final String myExample;

  @NotNull
  private final String myTemplateName;

  public @NotNull String @NotNull [] getTags() {
    return myTags;
  }

  private final @NotNull String @NotNull [] myTags;

  public JavaEditableTaggedPostfixTemplate(@NotNull String templateId,
                                           @NotNull String templateName,
                                           @NotNull String templateText,
                                           @NotNull String example,
                                           @NotNull Set<? extends JavaPostfixTemplateExpressionCondition> expressionConditions,
                                           @NotNull LanguageLevel minimumLanguageLevel,
                                           boolean useTopmostExpression,
                                           @NotNull String @NotNull [] tags,
                                           @NotNull PostfixTemplateProvider provider) {
    this(templateId, templateName, createTemplate(templateText), example, expressionConditions, minimumLanguageLevel, useTopmostExpression,
         tags, provider);
  }

  public JavaEditableTaggedPostfixTemplate(@NotNull String templateId,
                                           @NotNull String templateName,
                                           @NotNull TemplateImpl liveTemplate,
                                           @NotNull String example,
                                           @NotNull Set<? extends JavaPostfixTemplateExpressionCondition> expressionConditions,
                                           @NotNull LanguageLevel minimumLanguageLevel,
                                           boolean useTopmostExpression,
                                           @NotNull String @NotNull [] tags,
                                           @NotNull PostfixTemplateProvider provider) {
    super(templateId, templateName, liveTemplate, example.replace(EXPR_$, "expr"), expressionConditions, minimumLanguageLevel, useTopmostExpression, provider);
    myTags = tags;
    myExample = example;
    myTemplateName= templateName;
  }


  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    List<PsiElement> expressions = getExpressions(context, copyDocument, newOffset);
    if (expressions.size() != 1) return false;
    PsiElement element = expressions.get(0);
    myText = element.getText(); //not ideal, because the state is changed, but it needs for nice rendering
    return true;
  }

  @Override
  public void renderElement(@NotNull LookupElementPresentation presentation) {
    String exp = myText;
    String templateText = myExample;
    if (exp == null || templateText.length() + exp.length() >= 50 || !templateText.contains(EXPR_$)) {
      return;
    }
    String withExp = templateText.replace(EXPR_$, exp);
    if (withExp.contains("$")) {
      return;
    }
    ArrayList<String> allTags = new ArrayList<>(Arrays.asList(myTags));
    allTags.add(getKey());
    String message = JavaBundle.message("java.completion.tag", allTags.size());
    String withTags = withExp + " " + message + String.join(", ", allTags);
    presentation.setItemText(withTags);
    int startOffset = templateText.indexOf(EXPR_$);
    presentation.decorateItemTextRange(new TextRange(startOffset, startOffset + exp.length()),
                                       LookupElementPresentation.LookupItemDecoration.GRAY);
    startOffset = withExp.length();
    presentation.decorateItemTextRange(new TextRange(startOffset, startOffset + 1 + message.length()),
                                       LookupElementPresentation.LookupItemDecoration.GRAY);

    presentation.setTypeText("");
  }

  @Override
  public Collection<String> getAllLookupStrings() {
    return Arrays.asList(myTags);
  }
}
