// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.util;

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelector;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelectorBase;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatePsiInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
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
import org.jetbrains.annotations.Unmodifiable;

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

      @Override
      public @NotNull List<PsiElement> getExpressions(@NotNull PsiElement context, @NotNull Document document, int offset) {
        return PsiUtil.getLanguageLevel(context).isAtLeast(minimalLevel)
               ? selector.getExpressions(context, document, offset)
               : Collections.emptyList();
      }

      @Override
      public @NotNull Function<PsiElement, String> getRenderer() {
        return selector.getRenderer();
      }
    };
  }

  public static PostfixTemplateExpressionSelector selectorTopmost(Condition<? super PsiElement> additionalFilter) {
    return new PostfixTemplateExpressionSelectorBase(additionalFilter) {
      @Override
      protected @Unmodifiable List<PsiElement> getNonFilteredExpressions(@NotNull PsiElement context, @NotNull Document document, int offset) {
        return ContainerUtil.createMaybeSingletonList(getTopmostExpression(context));
      }

      @Override
      protected Condition<PsiElement> getFilters(int offset) {
        return and(super.getFilters(offset), getPsiErrorFilter());
      }

      @Override
      public @NotNull Function<PsiElement, String> getRenderer() {
        return JavaPostfixTemplatesUtils.getRenderer();
      }
    };
  }

  public static @NotNull PostfixTemplateExpressionSelector selectorAllExpressionsWithCurrentOffset(@Nullable Condition<? super PsiElement> additionalFilter) {
    return new PostfixTemplateExpressionSelectorBase(additionalFilter) {
      @Override
      protected List<PsiElement> getNonFilteredExpressions(@NotNull PsiElement context, @NotNull Document document, int offset) {
        return new ArrayList<>(CommonJavaRefactoringUtil.collectExpressions(context.getContainingFile(), document,
                                                                            Math.max(offset - 1, 0), false));
      }

      @Override
      public @Unmodifiable @NotNull List<PsiElement> getExpressions(@NotNull PsiElement context, @NotNull Document document, int offset) {
        List<PsiElement> expressions = super.getExpressions(context, document, offset);
        if (!expressions.isEmpty()) return expressions;

        return ContainerUtil.filter(ContainerUtil.<PsiElement>createMaybeSingletonList(getTopmostExpression(context)), getFilters(offset));
      }

      @Override
      public @NotNull Function<PsiElement, String> getRenderer() {
        return JavaPostfixTemplatesUtils.getRenderer();
      }
    };
  }

  public static final PostfixTemplatePsiInfo JAVA_PSI_INFO = new PostfixTemplatePsiInfo() {
    @Override
    public @NotNull PsiElement createExpression(@NotNull PsiElement context,
                                                @NotNull String prefix,
                                                @NotNull String suffix) {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
      return factory.createExpressionFromText(prefix + context.getText() + suffix, context);
    }

    @Override
    public @NotNull PsiExpression getNegatedExpression(@NotNull PsiElement element) {
      Project project = element.getProject();
      String negatedExpressionText = DumbService.getInstance(project)
        .computeWithAlternativeResolveEnabled(() -> BoolUtils.getNegatedExpressionText((PsiExpression)element));
      return JavaPsiFacade.getElementFactory(project).createExpressionFromText(negatedExpressionText, element);
    }
  };

  private static Condition<PsiElement> wrap(Condition<PsiElement> cond) {
    return e -> DumbService.getInstance(e.getProject()).computeWithAlternativeResolveEnabled(() -> cond.value(e));
  }

  public static final Condition<PsiElement> IS_BOOLEAN =
    wrap(element -> element instanceof PsiExpression expression && isBoolean(expression.getType()));

  /**
   * @deprecated use {@link #isThrowable(PsiType)}
   */
  @Deprecated(forRemoval = true)
  public static final Condition<PsiElement> IS_THROWABLE =
    wrap(element -> element instanceof PsiExpression expression && isThrowable(expression.getType()));

  public static final Condition<PsiElement> IS_NON_VOID =
    wrap(element -> element instanceof PsiExpression expression && isNonVoid(expression.getType()));

  public static final Condition<PsiElement> IS_NOT_PRIMITIVE =
    wrap(element -> element instanceof PsiExpression expression && isNotPrimitiveTypeExpression(expression));

  /**
   * @deprecated use {@link #isIterable(PsiType)} / {@link #isArray(PsiType)}
   */
  @Deprecated(forRemoval = true)
  public static final Condition<PsiElement> IS_ITERABLE_OR_ARRAY = wrap(element -> {
    if (!(element instanceof PsiExpression expr)) return false;

    PsiType type = expr.getType();
    return isArray(type) || isIterable(type);
  });

  @Contract("null -> false")
  public static boolean isNotPrimitiveTypeExpression(@Nullable PsiExpression expression) {
    if (expression == null) {
      return false;
    }
    return DumbService.getInstance(expression.getProject()).computeWithAlternativeResolveEnabled(() -> {
      PsiType type = expression.getType();
      return type != null && !(type instanceof PsiPrimitiveType);
    });
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
  public static boolean isArrayReference(@Nullable PsiType type) {
    return type instanceof PsiArrayType arrayType && !(arrayType.getComponentType() instanceof PsiPrimitiveType);
  }

  @Contract("null -> false")
  public static boolean isBoolean(@Nullable PsiType type) {
    return type != null && (PsiTypes.booleanType().equals(type) || type.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN));
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

    String canonicalText = type.getCanonicalText();
    return CommonClassNames.JAVA_LANG_INTEGER.equals(canonicalText) ||
           CommonClassNames.JAVA_LANG_LONG.equals(canonicalText) ||
           CommonClassNames.JAVA_LANG_BYTE.equals(canonicalText);
  }

  public static @NotNull Function<PsiElement, String> getRenderer() {
    return element -> {
      assert element instanceof PsiExpression;
      return PsiExpressionTrimRenderer.render((PsiExpression)element);
    };
  }

  public static @Nullable PsiExpression getTopmostExpression(PsiElement context) {
    PsiExpressionStatement statement = PsiTreeUtil.getNonStrictParentOfType(context, PsiExpressionStatement.class);
    return statement != null ? statement.getExpression() : null;
  }
}

