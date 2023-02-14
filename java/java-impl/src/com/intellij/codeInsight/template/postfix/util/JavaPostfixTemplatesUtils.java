// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.util;

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelector;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelectorBase;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatePsiInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Condition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.util.Conditions.and;

public final class JavaPostfixTemplatesUtils {
  private JavaPostfixTemplatesUtils() {
  }

  public static PostfixTemplateExpressionSelector atLeastJava8Selector(final PostfixTemplateExpressionSelector selector) {
    return minimalLanguageLevelSelector(selector, LanguageLevel.JDK_1_8);
  }

  public static PostfixTemplateExpressionSelector minimalLanguageLevelSelector(@NotNull PostfixTemplateExpressionSelector selector,
                                                                               @NotNull LanguageLevel minimalLevel) {
    return new PostfixTemplateExpressionSelector() {
      @Override
      public boolean hasExpression(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
        return PsiUtil.getLanguageLevel(context).isAtLeast(minimalLevel) && selector.hasExpression(context, copyDocument, newOffset);
      }

      @NotNull
      @Override
      public List<PsiElement> getExpressions(@NotNull PsiElement context, @NotNull Document document, int offset) {
        return PsiUtil.getLanguageLevel(context).isAtLeast(minimalLevel)
               ? selector.getExpressions(context, document, offset)
               : Collections.emptyList();
      }

      @NotNull
      @Override
      public Function<PsiElement, String> getRenderer() {
        return selector.getRenderer();
      }
    };
  }

  public static PostfixTemplateExpressionSelector selectorTopmost(Condition<? super PsiElement> additionalFilter) {
    return new PostfixTemplateExpressionSelectorBase(additionalFilter) {
      @Override
      protected List<PsiElement> getNonFilteredExpressions(@NotNull PsiElement context, @NotNull Document document, int offset) {
        return ContainerUtil.createMaybeSingletonList(getTopmostExpression(context));
      }

      @Override
      protected Condition<PsiElement> getFilters(int offset) {
        return and(super.getFilters(offset), getPsiErrorFilter());
      }

      @NotNull
      @Override
      public Function<PsiElement, String> getRenderer() {
        return JavaPostfixTemplatesUtils.getRenderer();
      }
    };
  }

  @NotNull
  public static PostfixTemplateExpressionSelector selectorAllExpressionsWithCurrentOffset(@Nullable Condition<? super PsiElement> additionalFilter) {
    return new PostfixTemplateExpressionSelectorBase(additionalFilter) {
      @Override
      protected List<PsiElement> getNonFilteredExpressions(@NotNull PsiElement context, @NotNull Document document, int offset) {
        return new ArrayList<>(CommonJavaRefactoringUtil.collectExpressions(context.getContainingFile(), document,
                                                                            Math.max(offset - 1, 0), false));
      }

      @NotNull
      @Override
      public List<PsiElement> getExpressions(@NotNull PsiElement context, @NotNull Document document, int offset) {
        if (DumbService.getInstance(context.getProject()).isDumb()) return Collections.emptyList();

        List<PsiElement> expressions = super.getExpressions(context, document, offset);
        if (!expressions.isEmpty()) return expressions;

        return ContainerUtil.filter(ContainerUtil.<PsiElement>createMaybeSingletonList(getTopmostExpression(context)), getFilters(offset));
      }

      @NotNull
      @Override
      public Function<PsiElement, String> getRenderer() {
        return JavaPostfixTemplatesUtils.getRenderer();
      }
    };
  }

  public static final PostfixTemplatePsiInfo JAVA_PSI_INFO = new PostfixTemplatePsiInfo() {
    @NotNull
    @Override
    public PsiElement createExpression(@NotNull PsiElement context,
                                       @NotNull String prefix,
                                       @NotNull String suffix) {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
      return factory.createExpressionFromText(prefix + context.getText() + suffix, context);
    }

    @NotNull
    @Override
    public PsiExpression getNegatedExpression(@NotNull PsiElement element) {
      assert element instanceof PsiExpression;
      String negatedExpressionText = BoolUtils.getNegatedExpressionText((PsiExpression)element);
      return JavaPsiFacade.getElementFactory(element.getProject()).createExpressionFromText(negatedExpressionText, element);
    }
  };

  public static final Condition<PsiElement> IS_NUMBER =
    element -> element instanceof PsiExpression && isNumber(((PsiExpression)element).getType());

  public static final Condition<PsiElement> IS_BOOLEAN =
    element -> element instanceof PsiExpression && isBoolean(((PsiExpression)element).getType());

  /**
   * @deprecated use {@link #isThrowable(PsiType)}
   */
  @Deprecated(forRemoval = true)
  public static final Condition<PsiElement> IS_THROWABLE =
    element -> element instanceof PsiExpression && isThrowable(((PsiExpression)element).getType());

  public static final Condition<PsiElement> IS_NON_VOID =
    element -> element instanceof PsiExpression && isNonVoid(((PsiExpression)element).getType());

  public static final Condition<PsiElement> IS_NOT_PRIMITIVE =
    element -> element instanceof PsiExpression && isNotPrimitiveTypeExpression((PsiExpression)element);

  /**
   * @deprecated use {@link #isIterable(PsiType)} / {@link #isArray(PsiType)}
   */
  @Deprecated(forRemoval = true)
  public static final Condition<PsiElement> IS_ITERABLE_OR_ARRAY = element -> {
    if (!(element instanceof PsiExpression)) return false;

    PsiType type = ((PsiExpression)element).getType();
    return isArray(type) || isIterable(type);
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
    return type instanceof PsiArrayType;
  }

  @Contract("null -> false")
  public static boolean isBoolean(@Nullable PsiType type) {
    return type != null && (PsiTypes.booleanType().equals(type) || PsiTypes.booleanType().equals(PsiPrimitiveType.getUnboxedType(type)));
  }

  @Contract("null -> false")
  public static boolean isNonVoid(@Nullable PsiType type) {
    return type != null && !PsiTypes.voidType().equals(type);
  }

  @Contract("null -> false")
  public static boolean isNumber(@Nullable PsiType type) {
    if (type == null) {
      return false;
    }
    if (PsiTypes.intType().equals(type) || PsiTypes.byteType().equals(type) || PsiTypes.longType().equals(type)) {
      return true;
    }

    PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(type);
    return PsiTypes.intType().equals(unboxedType) || PsiTypes.byteType().equals(unboxedType) || PsiTypes.longType().equals(unboxedType);
  }

  @NotNull
  public static Function<PsiElement, String> getRenderer() {
    return element -> {
      assert element instanceof PsiExpression;
      return PsiExpressionTrimRenderer.render((PsiExpression)element);
    };
  }

  @Nullable
  public static PsiExpression getTopmostExpression(PsiElement context) {
    PsiExpressionStatement statement = PsiTreeUtil.getNonStrictParentOfType(context, PsiExpressionStatement.class);
    return statement != null ? statement.getExpression() : null;
  }
}

