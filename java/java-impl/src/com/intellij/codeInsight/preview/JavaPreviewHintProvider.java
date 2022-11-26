// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.preview;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiExpressionPattern;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.patterns.PsiMethodPattern;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.util.ColorMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;


public class JavaPreviewHintProvider implements PreviewHintProvider {

  private static final PsiMethodPattern DECODE_METHOD = PsiJavaPatterns.psiMethod()
    .definedInClass(Color.class.getName())
    .withName("decode");
  private static final PsiExpressionPattern.Capture<PsiExpression> DECODE_METHOD_CALL_PARAMETER =
    PsiJavaPatterns.psiExpression().methodCallParameter(0, DECODE_METHOD);
  private static final PsiMethodPattern GET_COLOR_METHOD = PsiJavaPatterns.psiMethod()
    .definedInClass(Color.class.getName())
    .withName("getColor");
  private static final PsiExpressionPattern.Capture<PsiExpression> GET_METHOD_CALL_PARAMETER =
    PsiJavaPatterns.psiExpression().methodCallParameter(0, GET_COLOR_METHOD);

  private static boolean isInsideDecodeOrGetColorMethod(PsiElement element) {
    if (PsiUtil.isJavaToken(element, JavaTokenType.STRING_LITERAL)) {
      element = element.getParent();
    }

    return DECODE_METHOD_CALL_PARAMETER.accepts(element) ||
           GET_METHOD_CALL_PARAMETER.accepts(element);
  }

  @Override
  public boolean isSupportedFile(PsiFile file) {
    return file instanceof PsiJavaFile;
  }

  @SuppressWarnings("UseJBColor")
  @Override
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
                  if (expressions.length != 1) {
                    values[i] = values[i] > 255 ? 255 : Math.max(values[i], 0);
                  }

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
                if (i == 1 && values[0] > 255) {
                  c = new Color(values[0]);
                } else {
                  c = switch (values.length) {
                    case 1 -> new Color(values[0]);
                    case 3 -> new Color(values[0], values[1], values[2]);
                    case 4 -> new Color(values[0], values[1], values[2], values[3]);
                    default -> null;
                  };
                }
              }
              else if (j == expressions.length) {
                c = switch (values2.length) {
                  case 3 -> new Color(values2[0], values2[1], values2[2]);
                  case 4 -> new Color(values2[0], values2[1], values2[2], values2[3]);
                  default -> null;
                };
              }

              if (c != null) {
                return new ColorPreviewComponent(c);
              }
            }
          }
        }
      }
    }

    if (isInsideDecodeOrGetColorMethod(element)) {
      final String color = StringUtil.unquoteString(element.getText());
      try {
        return new ColorPreviewComponent(Color.decode(color));
      } catch (NumberFormatException ignore) {}
    }

    if (PlatformPatterns.psiElement(PsiIdentifier.class).withParent(PlatformPatterns.psiElement(PsiReferenceExpression.class))
      .accepts(element)) {
      final PsiReference reference = element.getParent().getReference();

      if (reference != null) {
        final PsiElement psiElement = reference.resolve();

        if (psiElement instanceof PsiField) {
          if ("java.awt.Color".equals(((PsiField)psiElement).getContainingClass().getQualifiedName())) {
            final String colorName = StringUtil.toLowerCase(((PsiField)psiElement).getName()).replace("_", "");
            final String hex = ColorMap.getHexCodeForColorName(colorName);
            if (hex != null) {
              return new ColorPreviewComponent(Color.decode("0x" + hex.substring(1)));
            }
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
