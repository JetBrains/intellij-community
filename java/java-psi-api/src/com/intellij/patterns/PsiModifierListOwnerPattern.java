// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.patterns;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PsiModifierListOwnerPattern<T extends PsiModifierListOwner, Self extends PsiModifierListOwnerPattern<T,Self>> extends PsiElementPattern<T,Self> {
  public PsiModifierListOwnerPattern(@NotNull final InitialPatternCondition<T> condition) {
    super(condition);
  }

  protected PsiModifierListOwnerPattern(final Class<T> aClass) {
    super(aClass);
  }

  public Self withModifiers(final String... modifiers) {
    return with(new PatternCondition<T>("withModifiers") {
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        return ContainerUtil.and(modifiers, s -> t.hasModifierProperty(s));
      }
    });
  }

  public Self withoutModifiers(final String... modifiers) {
    return with(new PatternCondition<T>("withoutModifiers") {
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        return ContainerUtil.and(modifiers, s -> !t.hasModifierProperty(s));
      }
    });
  }

  public Self withAnnotation(@NonNls final String qualifiedName) {
    return with(new PatternCondition<T>("withAnnotation") {
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        final PsiModifierList modifierList = t.getModifierList();
        return modifierList != null && modifierList.hasAnnotation(qualifiedName);
      }
    });
  }

  public Self withAnnotations(@NonNls final String... qualifiedNames) {
    return with(new PatternCondition<T>("withAnnotations") {
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
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
