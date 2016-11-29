/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

public class InstanceofExpressionPostfixTemplate extends PostfixTemplate {

  public InstanceofExpressionPostfixTemplate() {
    this("instanceof");
  }

  public InstanceofExpressionPostfixTemplate(String alias) {
    super(alias, "expr instanceof Type ? ((Type) expr). : null");
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    return JavaPostfixTemplatesUtils.isNotPrimitiveTypeExpression(JavaPostfixTemplatesUtils.getTopmostExpression(context));
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    PsiExpression expression = JavaPostfixTemplatesUtils.getTopmostExpression(context);
    if (!JavaPostfixTemplatesUtils.isNotPrimitiveTypeExpression(expression)) return;
    surroundExpression(context.getProject(), editor, expression);
  }

  private static void surroundExpression(@NotNull Project project, @NotNull Editor editor, @NotNull PsiExpression expr)
    throws IncorrectOperationException {
    assert expr.isValid();
    PsiType[] types = GuessManager.getInstance(project).guessTypeToCast(expr);
    final boolean parenthesesNeeded = expr instanceof PsiPolyadicExpression ||
                                      expr instanceof PsiConditionalExpression ||
                                      expr instanceof PsiAssignmentExpression;
    String exprText = parenthesesNeeded ? "(" + expr.getText() + ")" : expr.getText();
    Template template = generateTemplate(project, exprText, types);
    TextRange range;
    if (expr.isPhysical()) {
      range = expr.getTextRange();
    }
    else {
      RangeMarker rangeMarker = expr.getUserData(ElementToWorkOn.TEXT_RANGE);
      if (rangeMarker == null) {
        PostfixTemplatesUtils.showErrorHint(project, editor);
        return;
      }
      range = new TextRange(rangeMarker.getStartOffset(), rangeMarker.getEndOffset());
    }
    editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    editor.getCaretModel().moveToOffset(range.getStartOffset());
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    TemplateManager.getInstance(project).startTemplate(editor, template);
  }

  private static Template generateTemplate(Project project, String exprText, PsiType[] suggestedTypes) {
    TemplateManager templateManager = TemplateManager.getInstance(project);
    Template template = templateManager.createTemplate("", "");
    template.setToReformat(true);

    Set<LookupElement> itemSet = new LinkedHashSet<>();
    for (PsiType type : suggestedTypes) {
      itemSet.add(PsiTypeLookupItem.createLookupItem(type, null));
    }
    final LookupElement[] lookupItems = itemSet.toArray(new LookupElement[itemSet.size()]);
    final Result result = suggestedTypes.length > 0 ? new PsiTypeResult(suggestedTypes[0], project) : null;

    Expression expr = new Expression() {
      @Override
      public LookupElement[] calculateLookupItems(ExpressionContext context) {
        return lookupItems.length > 1 ? lookupItems : null;
      }

      @Override
      public Result calculateResult(ExpressionContext context) {
        return result;
      }

      @Override
      public Result calculateQuickResult(ExpressionContext context) {
        return null;
      }
    };

    template.addTextSegment(exprText);
    template.addTextSegment(" instanceof ");
    String type = "type";
    template.addVariable(type, expr, expr, true);
    template.addTextSegment(" ? ((");
    template.addVariableSegment(type);
    template.addTextSegment(")" + exprText + ")");
    template.addEndVariable();
    template.addTextSegment(" : null;");

    return template;
  }
}