/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifierList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class PsiMemberPattern<T extends PsiMember, Self extends PsiMemberPattern<T,Self>> extends PsiElementPattern<T,Self> {
  public PsiMemberPattern(@NotNull final NullablePatternCondition condition) {
    super(condition);
  }

  protected PsiMemberPattern(final Class<T> aClass) {
    super(aClass);
  }

  public Self withModifiers(final String... modifiers) {
    return with(new PatternCondition<T>() {
      public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return ContainerUtil.and(modifiers, new Condition<String>() {
          public boolean value(final String s) {
            return t.hasModifierProperty(s);
          }
        });
      }
    });
  }

  public Self withoutModifiers(final String... modifiers) {
    return with(new PatternCondition<T>() {
      public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return ContainerUtil.and(modifiers, new Condition<String>() {
          public boolean value(final String s) {
            return !t.hasModifierProperty(s);
          }
        });
      }
    });
  }

  public Self withAnnotation(@NonNls final String qualifiedName) {
    return with(new PatternCondition<T>() {
      public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        final PsiModifierList modifierList = t.getModifierList();
        return modifierList != null && modifierList.findAnnotation(qualifiedName) != null;
      }
    });
  }

  public Self inClass(final @NonNls String qname) {
    return inClass(PsiJavaPatterns.psiClass().withQualifiedName(qname));
  }

  public Self inClass(final ElementPattern pattern) {
    return with(new PatternCondition<T>() {
      public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return pattern.accepts(t.getContainingClass(), matchingContext, traverseContext);
      }
    });
  }

  public static class Capture extends PsiMemberPattern<PsiMember, Capture> {

    protected Capture() {
      super(new NullablePatternCondition() {
        public boolean accepts(@Nullable final Object o,
                                  final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
          return o instanceof PsiMember;
        }
      });
    }
  }
}
