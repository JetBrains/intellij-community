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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 26, 2002
 * Time: 2:16:32 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddTypeCastFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final PsiType myType;

  public AddTypeCastFix(@NotNull PsiType type, @NotNull PsiExpression expression) {
    super(expression);
    myType = type;
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("add.typecast.text", myType.getCanonicalText());
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("add.typecast.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return myType.isValid() && startElement.isValid() && startElement.getManager().isInProject(startElement);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable("is null when called from inspection") Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    addTypeCast(project, (PsiExpression)startElement, myType);
  }

  private static void addTypeCast(Project project, PsiExpression originalExpression, PsiType type) throws IncorrectOperationException {
    PsiExpression typeCast = createCastExpression(originalExpression, project, type);
    originalExpression.replace(typeCast);
  }

  static PsiExpression createCastExpression(PsiExpression originalExpression, Project project, PsiType type) throws IncorrectOperationException {
    // remove nested casts
    PsiElement element = PsiUtil.deparenthesizeExpression(originalExpression);
    if (element == null){
      return null;
    }
    PsiElementFactory factory = JavaPsiFacade.getInstance(originalExpression.getProject()).getElementFactory();

    PsiTypeCastExpression typeCast = (PsiTypeCastExpression)factory.createExpressionFromText("(Type)value", null);
    typeCast = (PsiTypeCastExpression)CodeStyleManager.getInstance(project).reformat(typeCast);
    typeCast.getCastType().replace(factory.createTypeElement(type));

    if (element instanceof PsiConditionalExpression) {
      // we'd better cast one branch of ternary expression if we could
      PsiConditionalExpression expression = (PsiConditionalExpression)element.copy();
      PsiExpression thenE = expression.getThenExpression();
      PsiExpression elseE = expression.getElseExpression();
      PsiType thenType = thenE == null ? null : thenE.getType();
      PsiType elseType = elseE == null ? null : elseE.getType();
      if (elseType != null && thenType != null) {
        boolean replaceThen = !TypeConversionUtil.isAssignable(type, thenType);
        boolean replaceElse = !TypeConversionUtil.isAssignable(type, elseType);
        if (replaceThen != replaceElse) {
          if (replaceThen) {
            typeCast.getOperand().replace(thenE);
            thenE.replace(typeCast);
          }
          else {
            typeCast.getOperand().replace(elseE);
            elseE.replace(typeCast);
          }
          return expression;
        }
      }
    }
    typeCast.getOperand().replace(element);
    return typeCast;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

}
