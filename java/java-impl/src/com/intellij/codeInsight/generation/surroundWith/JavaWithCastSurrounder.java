/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import java.util.LinkedHashSet;
import java.util.Set;

class JavaWithCastSurrounder extends JavaExpressionSurrounder {
  @NonNls private static final String TYPE_TEMPLATE_VARIABLE = "type";

  public boolean isApplicable(PsiExpression expr) {
    return true;
  }

  public TextRange surroundExpression(final Project project, final Editor editor, PsiExpression expr) throws IncorrectOperationException {
    PsiType[] types = GuessManager.getInstance(project).guessTypeToCast(expr);
    final Template template = generateTemplate(project, expr.getText(), types);
    TextRange range = expr.getTextRange();
    editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    editor.getCaretModel().moveToOffset(range.getStartOffset());
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    TemplateManager.getInstance(project).startTemplate(editor, template);
    return null;
  }

  private static Template generateTemplate(Project project, String exprText, final PsiType[] suggestedTypes) {
    final TemplateManager templateManager = TemplateManager.getInstance(project);
    final Template template = templateManager.createTemplate("", "");
    template.setToReformat(true);

    Set<LookupElement> itemSet = new LinkedHashSet<LookupElement>();
    for (PsiType type : suggestedTypes) {
      itemSet.add(PsiTypeLookupItem.createLookupItem(type, null));
    }
    final LookupElement[] lookupItems = itemSet.toArray(new LookupElement[itemSet.size()]);

    final Result result = suggestedTypes.length > 0 ? new PsiTypeResult(suggestedTypes[0], project) : null;

    Expression expr = new Expression() {
      public LookupElement[] calculateLookupItems(ExpressionContext context) {
        return lookupItems.length > 1 ? lookupItems : null;
      }

      public Result calculateResult(ExpressionContext context) {
        return result;
      }

      public Result calculateQuickResult(ExpressionContext context) {
        return null;
      }
    };
    template.addTextSegment("((");
    template.addVariable(TYPE_TEMPLATE_VARIABLE, expr, expr, true);
    template.addTextSegment(")" + exprText + ")");
    template.addEndVariable();

    return template;
  }

  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.cast.template");
  }
}
