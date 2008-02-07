/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifierList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class PsiMemberPattern<T extends PsiMember, Self extends PsiMemberPattern<T,Self>> extends PsiElementPattern<T,Self> {
  public PsiMemberPattern(@NotNull final InitialPatternCondition<T> condition) {
    super(condition);
  }

  protected PsiMemberPattern(final Class<T> aClass) {
    super(aClass);
  }

  public Self withModifiers(final String... modifiers) {
    return with(new PatternCondition<T>("withModifiers") {
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        return ContainerUtil.and(modifiers, new Condition<String>() {
          public boolean value(final String s) {
            return t.hasModifierProperty(s);
          }
        });
      }
    });
  }

  public Self withoutModifiers(final String... modifiers) {
    return with(new PatternCondition<T>("withoutModifiers") {
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        return ContainerUtil.and(modifiers, new Condition<String>() {
          public boolean value(final String s) {
            return !t.hasModifierProperty(s);
          }
        });
      }
    });
  }

  public Self withAnnotation(@NonNls final String qualifiedName) {
    return with(new PatternCondition<T>("withAnnotation") {
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        final PsiModifierList modifierList = t.getModifierList();
        return modifierList != null && modifierList.findAnnotation(qualifiedName) != null;
      }
    });
  }

  public Self inClass(final @NonNls String qname) {
    return inClass(PsiJavaPatterns.psiClass().withQualifiedName(qname));
  }

  public Self inClass(final ElementPattern pattern) {
    return with(new PatternCondition<T>("inClass") {
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        return pattern.getCondition().accepts(t.getContainingClass(), context);
      }
    });
  }

  public static class Capture extends PsiMemberPattern<PsiMember, Capture> {

    protected Capture() {
      super(new InitialPatternCondition<PsiMember>(PsiMember.class) {
        public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
          return o instanceof PsiMember;
        }
      });
    }
  }
}
