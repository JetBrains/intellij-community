// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.javadoc;

import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author spleaner
 */
public class ColorUtil {
  private ColorUtil() {
  }

  public static String generatePreviewHtml(@NotNull final Color color) {
    return DocumentationMarkup.SECTION_HEADER_START + "Preview:" + DocumentationMarkup.SECTION_SEPARATOR + "<p>" + 
           String.format("<div style=\"padding: 1px; width: 52px; height: 32px; background-color: #555555;\"><div style=\"width: 50px; height: 30px; background-color: #%s;\">&nbsp;</div></div>", com.intellij.ui.ColorUtil.toHex(color)) +
           DocumentationMarkup.SECTION_END;
  }

  @SuppressWarnings("UseJBColor")
  public static void appendColorPreview(final PsiVariable variable, final StringBuilder buffer) {
    final PsiExpression initializer = variable.getInitializer();
    if (initializer != null) {
      final PsiType type = initializer.getType();
      if (type != null && "java.awt.Color".equals(type.getCanonicalText())) {
        if (initializer instanceof PsiNewExpression) {
          final PsiExpressionList argumentList = ((PsiNewExpression) initializer).getArgumentList();
          if (argumentList != null) {
            final PsiExpression[] expressions = argumentList.getExpressions();
            int[] values = ArrayUtil.newIntArray(expressions.length);
            float[] values2 = new float[expressions.length];
            int i = 0;
            int j = 0;

            final PsiConstantEvaluationHelper helper = JavaPsiFacade.getInstance(initializer.getProject()).getConstantEvaluationHelper();
            for (final PsiExpression each : expressions) {
              final Object o = helper.computeConstantExpression(each);
              if (o instanceof Integer) {
                values[i] = ((Integer) o).intValue();
                values[i] = values[i] > 255 && expressions.length > 1 ? 255 : values[i] < 0 ? 0 : values[i];
                i++;
              } else if (o instanceof Float) {
                values2[j] = ((Float) o).floatValue();
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
            } else if (j == expressions.length) {
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
              buffer.append(generatePreviewHtml(c));
            }
          }
        } else if (initializer instanceof PsiReferenceExpression) {
          final PsiReference reference = initializer.getReference();
          if (reference != null) {
            final PsiElement psiElement = reference.resolve();
            if (psiElement instanceof PsiField) {
              PsiField psiField = (PsiField)psiElement;
              final PsiClass psiClass = psiField.getContainingClass();
              if (psiClass != null && "java.awt.Color".equals(psiClass.getQualifiedName())) {
                Color c = ReflectionUtil.getStaticFieldValue(Color.class, Color.class, psiField.getName());
                if (c != null) {
                  buffer.append(generatePreviewHtml(c));
                }
              }
            }
          }
        }
      }
    }
  }
}
