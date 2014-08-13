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
package com.intellij.codeInsight.template.postfix.util;

import com.intellij.codeInsight.CodeInsightServicesUtil;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatePsiInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class JavaPostfixTemplatesUtils {
  private JavaPostfixTemplatesUtils() {
  }

  public static final PostfixTemplatePsiInfo JAVA_PSI_INFO = new PostfixTemplatePsiInfo() {

    @NotNull
    @Override
    public PsiElement createStatement(@NotNull PsiElement context,
                                      @NotNull String prefix,
                                      @NotNull String suffix) {
      PsiElementFactory factory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
      return factory.createStatementFromText(prefix + context.getText() + suffix, context);
    }

    @NotNull
    @Override
    public PsiElement createExpression(@NotNull PsiElement context,
                                       @NotNull String prefix,
                                       @NotNull String suffix) {
      PsiElementFactory factory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
      return factory.createExpressionFromText(prefix + context.getText() + suffix, context);
    }

    @Nullable
    @Override
    public PsiExpression getTopmostExpression(@NotNull PsiElement element) {
      return JavaPostfixTemplatesUtils.getTopmostExpression(element);
    }

    @NotNull
    @Override
    public PsiExpression getNegatedExpression(@NotNull PsiElement element) {
      assert element instanceof PsiExpression;
      return CodeInsightServicesUtil.invertCondition((PsiExpression)element);
    }

    @NotNull
    @Override
    public List<PsiElement> getExpressions(@NotNull PsiElement context, @NotNull Document document, int newOffset) {
      return ContainerUtil.<PsiElement>newArrayList(IntroduceVariableBase.collectExpressions(context.getContainingFile(), document,
                                                                                             Math.max(newOffset - 1, 0), false));
    }

    @NotNull
    @Override
    public Function<PsiElement, String> getRenderer() {
      return new Function<PsiElement, String>() {
        @Override
        public String fun(PsiElement element) {
          assert element instanceof PsiExpression;
          return new PsiExpressionTrimRenderer.RenderFunction().fun((PsiExpression)element);
        }
      };
    }
  };

  public static Condition<PsiElement> IS_NUMBER = new Condition<PsiElement>() {
    @Override
    public boolean value(PsiElement element) {
      return element instanceof PsiExpression && isNumber(((PsiExpression)element).getType());
    }
  };

  public static Condition<PsiElement> IS_BOOLEAN = new Condition<PsiElement>() {
    @Override
    public boolean value(PsiElement element) {
      return element instanceof PsiExpression && isBoolean(((PsiExpression)element).getType());
    }
  };

  public static Condition<PsiElement> IS_THROWABLE = new Condition<PsiElement>() {
    @Override
    public boolean value(PsiElement element) {
      return element instanceof PsiExpression && isThrowable(((PsiExpression)element).getType());
    }
  };

  public static Condition<PsiElement> IS_NON_VOID = new Condition<PsiElement>() {
    @Override
    public boolean value(PsiElement element) {
      return element instanceof PsiExpression && isNonVoid(((PsiExpression)element).getType());
    }
  };

  public static Condition<PsiElement> IS_NOT_PRIMITIVE = new Condition<PsiElement>() {
    @Override
    public boolean value(PsiElement element) {
      return element instanceof PsiExpression && isNotPrimitiveTypeExpression((PsiExpression)element);
    }
  };

  public static Condition<PsiElement> IS_ITERABLE_OR_ARRAY = new Condition<PsiElement>() {
    @Override
    public boolean value(PsiElement element) {
      if (!(element instanceof PsiExpression)) return false;

      PsiType type = ((PsiExpression)element).getType();
      return isArray(type) || isIterable(type);
    }
  };

  @Contract("null -> false")
  public static boolean isNotPrimitiveTypeExpression(@Nullable PsiExpression expression) {
    if (expression == null) {
      return false;
    }
    PsiType type = expression.getType();
    return type != null && !(type instanceof PsiPrimitiveType);
  }

  @Contract("null -> false")
  public static boolean isIterable(@Nullable PsiType type) {
    return type != null && InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_ITERABLE);
  }

  @Contract("null -> false")
  public static boolean isThrowable(@Nullable PsiType type) {
    return type != null && InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_THROWABLE);
  }

  @Contract("null -> false")
  public static boolean isArray(@Nullable PsiType type) {
    return type != null && type instanceof PsiArrayType;
  }

  @Contract("null -> false")
  public static boolean isBoolean(@Nullable PsiType type) {
    return type != null && (PsiType.BOOLEAN.equals(type) || PsiType.BOOLEAN.equals(PsiPrimitiveType.getUnboxedType(type)));
  }

  @Contract("null -> false")
  public static boolean isNonVoid(@Nullable PsiType type) {
    return type != null && !PsiType.VOID.equals(type);
  }

  @Contract("null -> false")
  public static boolean isNumber(@Nullable PsiType type) {
    if (type == null) {
      return false;
    }
    if (PsiType.INT.equals(type) || PsiType.BYTE.equals(type) || PsiType.LONG.equals(type)) {
      return true;
    }

    PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(type);
    return PsiType.INT.equals(unboxedType) || PsiType.BYTE.equals(unboxedType) || PsiType.LONG.equals(unboxedType);
  }

  @Nullable
  public static PsiExpression getTopmostExpression(PsiElement context) {
    PsiExpressionStatement statement = PsiTreeUtil.getNonStrictParentOfType(context, PsiExpressionStatement.class);
    return statement != null ? statement.getExpression() : null;
  }
}

