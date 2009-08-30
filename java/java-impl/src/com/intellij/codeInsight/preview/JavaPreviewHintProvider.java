package com.intellij.codeInsight.preview;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.xml.util.ColorSampleLookupValue;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class JavaPreviewHintProvider implements PreviewHintProvider {
  public boolean isSupportedFile(PsiFile file) {
    return file instanceof PsiJavaFile;
  }

  public JComponent getPreviewComponent(@NotNull PsiElement element) {
    final PsiNewExpression psiNewExpression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class);

    if (psiNewExpression != null) {
      final PsiJavaCodeReferenceElement referenceElement = PsiTreeUtil.getChildOfType(psiNewExpression, PsiJavaCodeReferenceElement.class);

      if (referenceElement != null) {
        final PsiReference reference = referenceElement.getReference();

        if (reference != null) {
          final PsiElement psiElement = reference.resolve();

          if (psiElement instanceof PsiClass && "java.awt.Color".equals(((PsiClass)psiElement).getQualifiedName())) {
            final PsiExpressionList argumentList = psiNewExpression.getArgumentList();

            if (argumentList != null) {
              final PsiExpression[] expressions = argumentList.getExpressions();
              int[] values = ArrayUtil.newIntArray(expressions.length);
              float[] values2 = new float[expressions.length];
              int i = 0;
              int j = 0;

              final PsiConstantEvaluationHelper helper = JavaPsiFacade.getInstance(element.getProject()).getConstantEvaluationHelper();
              for (final PsiExpression each : expressions) {
                final Object o = helper.computeConstantExpression(each);
                if (o instanceof Integer) {
                  values[i] = ((Integer)o).intValue();
                  values[i] = values[i] > 255 ? 255 : values[i] < 0 ? 0 : values[i];
                  i++;
                }
                else if (o instanceof Float) {
                  values2[j] = ((Float)o).floatValue();
                  values2[j] = values2[j] > 1 ? 1 : values2[j] < 0 ? 0 : values2[j];
                  j++;
                }
              }


              Color c = null;
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

              if (c != null) {
                return new ColorPreviewComponent(null, c);
              }
            }
          }
        }
      }
    }

    if (PlatformPatterns.psiElement(PsiIdentifier.class).withParent(PlatformPatterns.psiElement(PsiReferenceExpression.class))
      .accepts(element)) {
      final PsiReference reference = element.getParent().getReference();

      if (reference != null) {
        final PsiElement psiElement = reference.resolve();

        if (psiElement instanceof PsiField) {
          if ("java.awt.Color".equals(((PsiField)psiElement).getContainingClass().getQualifiedName())) {
            final String colorName = ((PsiField)psiElement).getName().toLowerCase().replace("_", "");
            final String hex = ColorSampleLookupValue.getHexCodeForColorName(colorName);
            return new ColorPreviewComponent(null, Color.decode("0x" + hex.substring(1)));
          }
        }
      }
    }

    if (PlatformPatterns.psiElement().withParent(PlatformPatterns.psiElement(PsiLiteralExpression.class)).accepts(element)) {
      final PsiLiteralExpression psiLiteralExpression = (PsiLiteralExpression) element.getParent();
      if (psiLiteralExpression != null) {
        return ImagePreviewComponent.getPreviewComponent(psiLiteralExpression);
      }
    }

    return null;
  }
}
