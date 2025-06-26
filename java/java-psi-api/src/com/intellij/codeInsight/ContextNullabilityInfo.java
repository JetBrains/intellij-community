// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * A function that returns nullability info for a given context element
 */
@FunctionalInterface
public
interface ContextNullabilityInfo {
  /**
   * An empty context nullability info that returns {@code null} for all contexts
   */
  @NotNull ContextNullabilityInfo EMPTY = new ContextNullabilityInfo() {
    @Override
    public @Nullable NullabilityAnnotationInfo forContext(@NotNull PsiElement context) {
      return null;
    }

    @Override
    public @NotNull ContextNullabilityInfo orElse(@NotNull ContextNullabilityInfo other) {
      return other;
    }

    @Override
    public @NotNull ContextNullabilityInfo filtering(@NotNull Predicate<@NotNull PsiElement> contextFilter) {
      return this;
    }
  };

  /**
   * @param context context PSI element
   * @return nullability info for a given context element
   */
  @Nullable NullabilityAnnotationInfo forContext(@NotNull PsiElement context);

  /**
   * @param info constant nullability info to return for all contexts
   * @return a function that returns given nullability info for all contexts
   */
  static @NotNull ContextNullabilityInfo constant(@Nullable NullabilityAnnotationInfo info) {
    if (info == null) return EMPTY;
    return new ContextNullabilityInfo() {
      @Override
      public @NotNull NullabilityAnnotationInfo forContext(@NotNull PsiElement context) {
        return info;
      }

      @Override
      public @NotNull ContextNullabilityInfo orElse(@NotNull ContextNullabilityInfo other) {
        return this;
      }
    };
  }

  /**
   * @param contextFilter a predicate that determines whether nullability info is applicable to a given context
   * @return a new {@code ContextNullabilityInfo} that returns null for contexts that do not match the given predicate
   */
  default @NotNull ContextNullabilityInfo filtering(@NotNull Predicate<@NotNull PsiElement> contextFilter) {
    return context -> contextFilter.test(context) ? forContext(context) : null;
  }

  /**
   * @return a new {@code ContextNullabilityInfo} that filters out the cast contexts.
   */
  default @NotNull ContextNullabilityInfo disableInCast() {
    return filtering(context -> {
      PsiExpression parentExpression = PsiTreeUtil.getParentOfType(context, PsiExpression.class);
      return !(parentExpression instanceof PsiTypeCastExpression) || 
             !PsiTreeUtil.isAncestor(((PsiTypeCastExpression)parentExpression).getCastType(), context, false);
    });
  }

  /**
   * @param other a fallback context nullability info to use if this one is not applicable
   * @return a new {@code ContextNullabilityInfo} that returns the result of this one if it is applicable, or the result of the other one otherwise
   */
  default @NotNull ContextNullabilityInfo orElse(@NotNull ContextNullabilityInfo other) {
    if (other == EMPTY) return this;
    return context -> {
      NullabilityAnnotationInfo info = forContext(context);
      return info != null ? info : other.forContext(context);
    };
  }
}
