// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.patterns;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.StandardPatterns.string;

public class PsiClassPattern extends PsiMemberPattern<PsiClass, PsiClassPattern>{
  protected PsiClassPattern() {
    super(PsiClass.class);
  }

  public PsiClassPattern inheritorOf(final boolean strict, final PsiClassPattern pattern) {
    return with(new PatternCondition<PsiClass>("inheritorOf") {
      @Override
      public boolean accepts(@NotNull PsiClass psiClass, final ProcessingContext context) {
        return isInheritor(psiClass, pattern, context, !strict);
      }
    });
  }

  private static boolean isInheritor(PsiClass psiClass, ElementPattern pattern, final ProcessingContext matchingContext, boolean checkThisClass) {
    if (psiClass == null) return false;
    if (checkThisClass && pattern.accepts(psiClass, matchingContext)) return true;
    if (isInheritor(psiClass.getSuperClass(), pattern, matchingContext, true)) return true;
    for (final PsiClass aClass : psiClass.getInterfaces()) {
      if (isInheritor(aClass, pattern, matchingContext, true)) return true;
    }
    return false;
  }

  public PsiClassPattern inheritorOf(final boolean strict, final String className) {
    return with(new PatternCondition<PsiClass>("inheritorOf") {
      @Override
      public boolean accepts(@NotNull PsiClass psiClass, final ProcessingContext context) {
        return InheritanceUtil.isInheritor(psiClass, strict, className);
      }
    });
  }

  public PsiClassPattern isInterface() {
    return with(new PatternCondition<PsiClass>("isInterface") {
      @Override
      public boolean accepts(@NotNull final PsiClass psiClass, final ProcessingContext context) {
        return psiClass.isInterface();
      }
    });}
  public PsiClassPattern isAnnotationType() {
    return with(new PatternCondition<PsiClass>("isAnnotationType") {
      @Override
      public boolean accepts(@NotNull final PsiClass psiClass, final ProcessingContext context) {
        return psiClass.isAnnotationType();
      }
    });}

  public PsiClassPattern withMethod(final boolean checkDeep, final ElementPattern<? extends PsiMethod> memberPattern) {
    return with(new PatternCondition<PsiClass>("withMethod") {
      @Override
      public boolean accepts(@NotNull final PsiClass psiClass, final ProcessingContext context) {
        for (PsiMethod method : (checkDeep ? psiClass.getAllMethods() : psiClass.getMethods())) {
          if (memberPattern.accepts(method, context)) {
            return true;
          }
        }
        return false;
      }
    });
  }
  public PsiClassPattern withField(final boolean checkDeep, final ElementPattern<? extends PsiField> memberPattern) {
    return with(new PatternCondition<PsiClass>("withField") {
      @Override
      public boolean accepts(@NotNull final PsiClass psiClass, final ProcessingContext context) {
        for (PsiField field : (checkDeep ? psiClass.getAllFields() : psiClass.getFields())) {
          if (memberPattern.accepts(field, context)) {
            return true;
          }
        }
        return false;
      }
    });
  }

  public PsiClassPattern nonAnnotationType() {
    return with(new PatternCondition<PsiClass>("nonAnnotationType") {
      @Override
      public boolean accepts(@NotNull final PsiClass psiClass, final ProcessingContext context) {
        return !psiClass.isAnnotationType();
      }
    });
  }

  public PsiClassPattern withQualifiedName(@NonNls @NotNull final String qname) {
    return with(new PsiClassNamePatternCondition(string().equalTo(qname)));
  }

  public PsiClassPattern withQualifiedName(@NonNls @NotNull final ElementPattern<String> qname) {
    return with(new PsiClassNamePatternCondition(qname));
  }
}
