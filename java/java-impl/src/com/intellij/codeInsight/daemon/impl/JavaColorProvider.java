// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ElementColorProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.evaluation.UEvaluationContextKt;
import org.jetbrains.uast.values.UConstant;
import org.jetbrains.uast.values.UValue;

import java.awt.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("UseJBColor")
public class JavaColorProvider implements ElementColorProvider {
  @Override
  public Color getColorFrom(@NotNull PsiElement element) {
    if (element.getFirstChild() != null) return null;
    PsiElement parent = element.getParent();
    Color color = getJavaColorFromExpression(parent);
    if (color == null) {
      parent = parent == null ? null : parent.getParent();
      color = getJavaColorFromExpression(parent);
    }
    UCallExpression newExpression = UastContextKt.toUElement(parent, UCallExpression.class);
    if (newExpression != null) {
      UReferenceExpression uRef = newExpression.getClassReference();
      String resolvedName = uRef == null ? null : uRef.getResolvedName();
      if (resolvedName != null && element.textMatches(resolvedName)) {
        return color;
      }
    }

    if (isIntLiteralInsideNewJBColorExpression(parent)) {
      return color;
    }
    return null;
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
    UCallExpression newExpression = UastContextKt.toUElement(element, UCallExpression.class);
    if (newExpression != null && newExpression.getKind() == UastCallKind.CONSTRUCTOR_CALL &&
        isColorType(newExpression.getReturnType())) {
      return getColor(newExpression.getValueArguments());
    }
    if (isIntLiteralInsideNewJBColorExpression(element)) {
      final String text = element.getText();
      boolean hasAlpha = text != null && StringUtil.startsWithIgnoreCase(text, "0x") && text.length() > 8;
      ULiteralExpression literal = UastContextKt.toUElement(element, ULiteralExpression.class);
      Object object = getObject(literal);
      if (object instanceof Integer) return new Color(((Integer)object).intValue(), hasAlpha);
    }
    return null;
  }

  private static boolean isIntLiteralInsideNewJBColorExpression(PsiElement element) {
    ULiteralExpression literalExpression = UastContextKt.toUElement(element, ULiteralExpression.class);
    if (literalExpression != null && PsiType.INT.equals(literalExpression.getExpressionType())) {
      UElement parent = literalExpression.getUastParent();
      if (parent != null) {
        return isNewJBColorExpression(parent);
      }
    }
    return false;
  }

  private static boolean isNewJBColorExpression(UElement element) {
    if (element instanceof UCallExpression) {
      UCallExpression callExpression = (UCallExpression)element;
      if (callExpression.getKind() == UastCallKind.CONSTRUCTOR_CALL) {
        final PsiClass psiClass = PsiTypesUtil.getPsiClass(callExpression.getReturnType());
        return psiClass != null && JBColor.class.getName().equals(psiClass.getQualifiedName());
      }
    }
    return false;
  }

  @Nullable
  private static Color getColor(List<UExpression> args) {
    try {
      ColorConstructors type = args.isEmpty() ? null : getConstructorType(args.size(), args.get(0).getExpressionType());
      if (type != null) {
        switch (type) {
          case INT:      return new Color(  getInt(args.get(0)));
          case INT_BOOL: return new Color(  getInt(args.get(0)),   getBoolean(args.get(1)));
          case INT_x3:   return new Color(  getInt(args.get(0)),   getInt(args.get(1)),   getInt(args.get(2)));
          case INT_x4:   return new Color(  getInt(args.get(0)),   getInt(args.get(1)),   getInt(args.get(2)), getInt(args.get(3)));
          case FLOAT_x3: return new Color(getFloat(args.get(0)), getFloat(args.get(1)), getFloat(args.get(2)));
          case FLOAT_x4: return new Color(getFloat(args.get(0)), getFloat(args.get(1)), getFloat(args.get(2)), getFloat(args.get(3)));
        }
      }
    }
    catch (Exception ignore) {
    }
    return null;
  }

  @Nullable
  private static ColorConstructors getConstructorType(int paramCount, PsiType paramType) {
    switch (paramCount) {
      case 1: return ColorConstructors.INT;
      case 2: return ColorConstructors.INT_BOOL;
      case 3: return PsiType.INT.equals(paramType) ? ColorConstructors.INT_x3 : ColorConstructors.FLOAT_x3;
      case 4: return PsiType.INT.equals(paramType) ? ColorConstructors.INT_x4 : ColorConstructors.FLOAT_x4;
    }

    return null;
  }

  public static int getInt(UExpression expr) {
    return ((Integer)getObject(expr)).intValue();
  }

  public static float getFloat(UExpression expr) {
    return ((Float)getObject(expr)).floatValue();
  }

  public static int getInt(PsiExpression expr) {
    return ((Integer)getObject(expr)).intValue();
  }

  public static float getFloat(PsiExpression expr) {
    return ((Float)getObject(expr)).floatValue();
  }

  public static boolean getBoolean(UExpression expr) {
    return ((Boolean)getObject(expr)).booleanValue();
  }

  private static Object getObject(PsiExpression expr) {
    return JavaConstantExpressionEvaluator.computeConstantExpression(expr, true);
  }

  private static Object getObject(UExpression expr) {
    UValue value = UEvaluationContextKt.uValueOf(expr);
    if (value == null) {
      return null;
    }
    UConstant constant = value.toConstant();
    if (constant == null) {
      return null;
    }
    return constant.getValue();
  }

  @Override
  public void setColorTo(@NotNull PsiElement element, @NotNull Color color) {
    Runnable command;
    final Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(element.getContainingFile());

    if (isIntLiteralInsideNewJBColorExpression(element)) {
      command = () -> replaceInt((PsiExpression)element, color.getRGB(), true, color.getAlpha() != 255);
    }
    else {
      PsiNewExpression expression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class);
      if (expression == null) return;
      PsiExpressionList argumentList = expression.getArgumentList();
      assert argumentList != null;

      PsiExpression[] expr = argumentList.getExpressions();
      PsiType[] expressionTypes = argumentList.getExpressionTypes();
      ColorConstructors type = expressionTypes.length == 0 ? null : getConstructorType(expressionTypes.length, expressionTypes[0]);

      assert type != null;
      command = () -> {
        switch (type) {
          case INT:
            if (color.getAlpha() == 255) {
              replaceInt(expr[0], color.getRGB(), true);
            }
            else {
              PsiElementFactory factory = JavaPsiFacade.getElementFactory(argumentList.getProject());
              argumentList.add(factory.createExpressionFromText("true", null));
              replaceInt(expr[0], color.getRGB() | color.getAlpha() << 24, true, true);
            }
            return;
          case INT_BOOL:
            if ("true".equals(expr[1].getText())) {
              replaceInt(expr[0], color.getRGB() | color.getAlpha() << 24, true, true);
            }
            else {
              if (color.getAlpha() == 255) {
                replaceInt(expr[0], color.getRGB(), true);
              }
              else {
                PsiElementFactory factory = JavaPsiFacade.getElementFactory(argumentList.getProject());
                expr[1].replace(factory.createExpressionFromText("true", null));
                replaceInt(expr[0], color.getRGB() | color.getAlpha() << 24, true, true);
              }
            }
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
              PsiElementFactory factory = JavaPsiFacade.getElementFactory(argumentList.getProject());
              String text = String.valueOf(color.getAlpha());
              argumentList.add(factory.createExpressionFromText(text, null));
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
              PsiElementFactory factory = JavaPsiFacade.getElementFactory(argumentList.getProject());
              String text = String.valueOf(color.getAlpha());
              argumentList.add(factory.createExpressionFromText(text + "f", null));
            }
        }
      };
    }
    CommandProcessor.getInstance()
      .executeCommand(element.getProject(), command, IdeBundle.message("change.color.command.text"), null, document);
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
      String text;
      if (hex) {
        text = "0x";
        Color c = new Color(newValue, hasAlpha);
        if (hasAlpha) {
          text += Integer.toHexString(c.getAlpha()).toUpperCase();
        }
        text += ColorUtil.toHex(c).toUpperCase();
      }
      else {
        text = Integer.toString(newValue);
      }

      expr.replace(factory.createExpressionFromText(text, null));
    }
  }
  private static void replaceFloat(PsiExpression expr, float newValue) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(expr.getProject());
    if (getFloat(expr) != newValue) {
      expr.replace(factory.createExpressionFromText(newValue + "f", null));
    }
  }

  private enum ColorConstructors {
    INT, INT_BOOL, INT_x3, INT_x4, FLOAT_x3, FLOAT_x4
  }
}