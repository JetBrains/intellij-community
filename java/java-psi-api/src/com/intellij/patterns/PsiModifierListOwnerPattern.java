// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PsiModifierListOwnerPattern<T extends PsiModifierListOwner, Self extends PsiModifierListOwnerPattern<T,Self>> extends PsiElementPattern<T,Self> {
  public PsiModifierListOwnerPattern(final @NotNull InitialPatternCondition<T> condition) {
    super(condition);
  }

  protected PsiModifierListOwnerPattern(final Class<T> aClass) {
    super(aClass);
  }

  public Self withModifiers(final String... modifiers) {
    return with(new PatternCondition<T>("withModifiers") {
      @Override
      public boolean accepts(final @NotNull T t, final ProcessingContext context) {
        return ContainerUtil.and(modifiers, s -> t.hasModifierProperty(s));
      }
    });
  }

  public Self withoutModifiers(final String... modifiers) {
    return with(new PatternCondition<T>("withoutModifiers") {
      @Override
      public boolean accepts(final @NotNull T t, final ProcessingContext context) {
        return ContainerUtil.and(modifiers, s -> !t.hasModifierProperty(s));
      }
    });
  }

  public Self withAnnotation(final @NonNls String qualifiedName) {
    return with(new PatternCondition<T>("withAnnotation") {
      @Override
      public boolean accepts(final @NotNull T t, final ProcessingContext context) {
        final PsiModifierList modifierList = t.getModifierList();
        return modifierList != null && modifierList.hasAnnotation(qualifiedName);
      }
    });
  }

  public Self withAnnotations(final @NonNls String... qualifiedNames) {
    return with(new PatternCondition<T>("withAnnotations") {
      @Override
      public boolean accepts(final @NotNull T t, final ProcessingContext context) {
        return AnnotationUtil.findAnnotation(t, qualifiedNames) != null;
      }
    });
  }

  public static class Capture<T extends PsiModifierListOwner> extends PsiModifierListOwnerPattern<T, Capture<T>> {
    public Capture(@NotNull InitialPatternCondition<T> condition) {
      super(condition);
    }
  }
}
