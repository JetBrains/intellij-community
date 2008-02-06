/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PsiClassPattern extends PsiMemberPattern<PsiClass, PsiClassPattern>{
  protected PsiClassPattern() {
    super(PsiClass.class);
  }

  public PsiClassPattern inheritorOf(final boolean strict, final PsiClassPattern pattern) {
    return with(new PatternCondition<PsiClass>("inheritorOf") {
      public boolean accepts(@NotNull PsiClass psiClass, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return isInheritor(strict ? psiClass.getSuperClass() : psiClass, pattern, matchingContext, traverseContext);
      }
    });
  }

  private static boolean isInheritor(PsiClass psiClass, ElementPattern pattern, final MatchingContext matchingContext, final TraverseContext context) {
    if (psiClass == null) return false;
    if (pattern.getCondition().accepts(psiClass, matchingContext, context)) return true;
    if (isInheritor(psiClass.getSuperClass(), pattern, matchingContext, context)) return true;
    for (final PsiClass aClass : psiClass.getInterfaces()) {
      if (isInheritor(aClass, pattern, matchingContext, context)) return true;
    }
    return false;
  }


  public PsiClassPattern inheritorOf(final boolean strict, final String className) {
    return inheritorOf(strict, PsiJavaPatterns.psiClass().withName(className));
  }

  public PsiClassPattern inheritorOf(final boolean strict, final PsiClass psiClass) {
    return inheritorOf(strict, PsiJavaPatterns.psiClass().equalTo(psiClass));
  }

  public PsiClassPattern isInterface() {
    return with(new PatternCondition<PsiClass>("isInterface") {
      public boolean accepts(@NotNull final PsiClass psiClass, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return psiClass.isInterface();
      }
    });
  }

  public PsiClassPattern withQualifiedName(@NonNls @NotNull final String qname) {
    return with(new PatternCondition<PsiClass>("withQualifiedName") {
      public boolean accepts(@NotNull final PsiClass psiClass,
                                final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return qname.equals(psiClass.getQualifiedName());
      }
    });
  }
  

}
