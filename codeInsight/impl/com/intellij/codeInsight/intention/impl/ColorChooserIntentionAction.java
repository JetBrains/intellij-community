/*
 * Created by IntelliJ IDEA.
 * User: spleaner
 * Date: Aug 22, 2007
 * Time: 3:44:10 PM
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.impl.StandardPatterns;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.ColorChooser;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class ColorChooserIntentionAction extends PsiElementBaseIntentionAction {

  public boolean isAvailable(@NotNull final Project project, final Editor editor, @Nullable final PsiElement element) {
    if (StandardPatterns.psiElement().inside(StandardPatterns.psiElement().type(PsiNewExpression.class)).accepts(element)) {
      final PsiNewExpression expression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class);
      assert expression != null;
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

    return false;
  }

  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.color.chooser.dialog");
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
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
      final PsiElementFactory factory = manager.getElementFactory();
      final PsiExpression newCall = factory.createExpressionFromText(
        "new java.awt.Color(" + color.getRed() + ", " + color.getGreen() + ", " + color.getBlue() + ")", expression);
      final PsiElement insertedElement = expression.replace(newCall);
      final CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
      codeStyleManager.reformat(insertedElement);
    }
  }
}