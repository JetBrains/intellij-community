/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ElementColorProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("UseJBColor")
public class JavaColorProvider implements ElementColorProvider {
  @Override
  public Color getColorFrom(@NotNull PsiElement element) {
    return getJavaColorFromExpression(element);
  }

  public static boolean isColorType(@Nullable PsiType type) {
    if (type != null) {
      final PsiClass aClass = PsiTypesUtil.getPsiClass(type);
      if (aClass != null) {
        final String fqn = aClass.getQualifiedName();
        if ("java.awt.Color".equals(fqn) || "javax.swing.plaf.ColorUIResource".equals(fqn)) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  public static Color getJavaColorFromExpression(@Nullable PsiElement element) {
    if (element instanceof PsiNewExpression) {
      final PsiNewExpression expr = (PsiNewExpression)element;
      if (isColorType(expr.getType())) {
        return getColor(expr.getArgumentList());
      }
    } else if (isIntLiteralInsideNewJBColorExpression(element)) {
      final String text = element.getText();
      boolean hasAlpha = text != null && StringUtil.startsWithIgnoreCase(text, "0x") && text.length() > 8;
      return new Color(getInt((PsiExpression)element), hasAlpha);
    }
    return null;
  }

  private static boolean isIntLiteralInsideNewJBColorExpression(PsiElement element) {
    if (element instanceof PsiLiteralExpression && PsiType.INT.equals(((PsiLiteralExpression)element).getType())) {
      PsiElement parent = element.getParent();
      if (parent != null) {
        return isNewJBColorExpression(parent.getParent());
      }
    }
    return false;
  }

  private static boolean isNewJBColorExpression(PsiElement element) {
    if (element instanceof PsiNewExpression) {
      final PsiClass psiClass = PsiTypesUtil.getPsiClass(((PsiNewExpression)element).getType());
      if (psiClass != null && JBColor.class.getName().equals(psiClass.getQualifiedName())) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static Color getColor(PsiExpressionList list) {
    try {
      final PsiExpression[] args = list.getExpressions();
      final PsiType[] types = list.getExpressionTypes();
      ColorConstructors type = getConstructorType(types);
      if (type != null) {
        switch (type) {
          case INT:      return new Color(  getInt(args[0]));
          case INT_BOOL: return new Color(  getInt(args[0]), getBoolean(args[1]));
          case INT_x3:   return new Color(  getInt(args[0]),     getInt(args[1]),   getInt(args[2]));
          case INT_x4:   return new Color(  getInt(args[0]),     getInt(args[1]),   getInt(args[2]),   getInt(args[3]));
          case FLOAT_x3: return new Color(getFloat(args[0]),   getFloat(args[1]), getFloat(args[2]));
          case FLOAT_x4: return new Color(getFloat(args[0]),   getFloat(args[1]), getFloat(args[2]), getFloat(args[3]));
        }
      }
    }
    catch (Exception ignore) {
    }
    return null;
  }

  @Nullable
  private static ColorConstructors getConstructorType(PsiType[] types) {
    int len = types.length;
    if (len == 0) return null;

    switch (len) {
      case 1: return ColorConstructors.INT;
      case 2: return ColorConstructors.INT_BOOL;
      case 3: return PsiType.INT.equals(types[0]) ? ColorConstructors.INT_x3 : ColorConstructors.FLOAT_x3;
      case 4: return PsiType.INT.equals(types[0]) ? ColorConstructors.INT_x4 : ColorConstructors.FLOAT_x4;
    }

    return null;
  }

  public static int getInt(PsiExpression expr) {
    return ((Integer)getObject(expr)).intValue();
  }

  public static float getFloat(PsiExpression expr) {
    return ((Float)getObject(expr)).floatValue();
  }

  public static boolean getBoolean(PsiExpression expr) {
    return ((Boolean)getObject(expr)).booleanValue();
  }

  private static Object getObject(PsiExpression expr) {
    return JavaConstantExpressionEvaluator.computeConstantExpression(expr, true);
  }

  @Override
  public void setColorTo(@NotNull PsiElement element, @NotNull Color color) {
    Runnable command;
    final Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(element.getContainingFile());

    if (isIntLiteralInsideNewJBColorExpression(element)) {
      command = () -> replaceInt((PsiExpression)element, color.getRGB(), true, color.getAlpha() != 255);
    } else {
      PsiExpressionList argumentList = ((PsiNewExpression)element).getArgumentList();
      assert argumentList != null;

      PsiExpression[] expr = argumentList.getExpressions();
      ColorConstructors type = getConstructorType(argumentList.getExpressionTypes());

      assert type != null;
      command = () -> {
        switch (type) {
          case INT:
          case INT_BOOL:
            replaceInt(expr[0], color.getRGB(), true);
            return;
          case INT_x3:
          case INT_x4:
            replaceInt(expr[0], color.getRed());
            replaceInt(expr[1], color.getGreen());
            replaceInt(expr[2], color.getBlue());
            if (type == ColorConstructors.INT_x4) {
              replaceInt(expr[3], color.getAlpha());
            }
            else if (color.getAlpha() != 255) {
              //todo add alpha
            }
            return;
          case FLOAT_x3:
          case FLOAT_x4:
            float[] rgba = color.getColorComponents(null);
            replaceFloat(expr[0], rgba[0]);
            replaceFloat(expr[1], rgba[1]);
            replaceFloat(expr[2], rgba[2]);
            if (type == ColorConstructors.FLOAT_x4) {
              replaceFloat(expr[3], rgba.length == 4 ? rgba[3] : 0f);
            }
            else if (color.getAlpha() != 255) {
              //todo add alpha
            }
        }
      };
    }
    if (command != null) {
      CommandProcessor.getInstance().executeCommand(element.getProject(), command, IdeBundle.message("change.color.command.text"), null, document);
    }
  }

  private static void replaceInt(PsiExpression expr, int newValue) {
    replaceInt(expr, newValue, false);
  }

  private static void replaceInt(PsiExpression expr, int newValue, boolean hex) {
    replaceInt(expr, newValue, hex, false);
  }

  private static void replaceInt(PsiExpression expr, int newValue, boolean hex, boolean hasAlpha) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(expr.getProject());
    if (getInt(expr) != newValue) {
      final Color c = new Color(newValue, hasAlpha);
      String text;
      if (hex) {
        text = "0x";
        if (hasAlpha) {
          text += Integer.toHexString(c.getAlpha());
        }
        text += ColorUtil.toHex(c).toUpperCase();
      } else {
        text = Integer.toString(newValue);
      }

      expr.replace(factory.createExpressionFromText(text, null));
    }
  }
  private static void replaceFloat(PsiExpression expr, float newValue) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(expr.getProject());
    if (getFloat(expr) != newValue) {
      expr.replace(factory.createExpressionFromText(String.valueOf(newValue) + "f", null));
    }
  }
  
  private enum ColorConstructors {
    INT, INT_BOOL, INT_x3, INT_x4, FLOAT_x3, FLOAT_x4
  }
}
