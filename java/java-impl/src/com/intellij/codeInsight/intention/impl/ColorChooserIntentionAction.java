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
 * User: spleaner
 * Date: Aug 22, 2007
 * Time: 3:44:10 PM
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.intention.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.*;
import com.intellij.patterns.*;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.util.*;
import com.intellij.ui.*;
import com.intellij.util.*;
import org.jetbrains.annotations.*;

import java.awt.*;

public class ColorChooserIntentionAction extends PsiElementBaseIntentionAction {

  public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) {
    if (PlatformPatterns.psiElement().inside(PlatformPatterns.psiElement(PsiNewExpression.class)).accepts(element)) {
      final PsiNewExpression expression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class, false);
      if (expression != null) {
        final PsiJavaCodeReferenceElement referenceElement = PsiTreeUtil.getChildOfType(expression, PsiJavaCodeReferenceElement.class);
        if (referenceElement != null) {
          final PsiReference reference = referenceElement.getReference();
          if (reference != null) {
            final PsiElement psiElement = reference.resolve();
            if (psiElement instanceof PsiClass && "java.awt.Color".equals(((PsiClass)psiElement).getQualifiedName())) {
              setText(CodeInsightBundle.message("intention.color.chooser.dialog"));
              return true;
            }
          }
        }
      }
    }

    return false;
  }

  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.color.chooser.dialog");
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiNewExpression expression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class);
    if (expression == null) return;

    Color c = null;

    final PsiExpressionList argumentList = expression.getArgumentList();
    if (argumentList != null) {
      final PsiExpression[] expressions = argumentList.getExpressions();
      int[] values = new int[expressions.length];
      float[] values2 = new float[expressions.length];
      int i = 0;
      int j = 0;
      for (final PsiExpression each : expressions) {
        if (each instanceof PsiLiteralExpression) {
          final Object o = ((PsiLiteralExpression)each).getValue();
          if (o instanceof Integer) {
            values[i] = ((Integer)o).intValue();
            i++;
          }
          else if (o instanceof Float) {
            values2[j] = ((Float)o).floatValue();
            j++;
          }
        }
      }

      if (i == expressions.length) {
        switch (values.length) {
          case 1:
            c = new Color(values[0]);
            break;
          case 3:
            c = new Color(values[0], values[1], values[2]);
            break;
          case 4:
            c = new Color(values[0], values[1], values[2], values[3]);
            break;
          default:
            break;
        }
      }
      else if (j == expressions.length) {
        switch (values2.length) {
          case 3:
            c = new Color(values2[0], values2[1], values2[2]);
            break;
          case 4:
            c = new Color(values2[0], values2[1], values2[2], values2[3]);
            break;
          default:
            break;
        }
      }
    }

    c = (c == null) ? Color.GRAY : c;

    final Color color = ColorChooser.chooseColor(editor.getComponent(), CodeInsightBundle.message("intention.color.chooser.dialog"), c);
    if (color != null) {
      final PsiManager manager = expression.getManager();
      final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
      final PsiExpression newCall = factory.createExpressionFromText(
          "new java.awt.Color(" + color.getRed() + ", " + color.getGreen() + ", " + color.getBlue() + ")", expression);
      final PsiElement insertedElement = expression.replace(newCall);
      final CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
      codeStyleManager.reformat(insertedElement);
    }
  }
}