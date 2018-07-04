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
package com.intellij.patterns;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.StandardPatterns.string;

/**
 * @author peter
 */
public class PsiClassPattern extends PsiMemberPattern<PsiClass, PsiClassPattern>{
  protected PsiClassPattern() {
    super(PsiClass.class);
  }

  public PsiClassPattern inheritorOf(final boolean strict, final PsiClassPattern pattern) {
    return with(new PatternCondition<PsiClass>("inheritorOf") {
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
      public boolean accepts(@NotNull PsiClass psiClass, final ProcessingContext context) {
        return InheritanceUtil.isInheritor(psiClass, strict, className);
      }
    });
  }

  public PsiClassPattern isInterface() {
    return with(new PatternCondition<PsiClass>("isInterface") {
      public boolean accepts(@NotNull final PsiClass psiClass, final ProcessingContext context) {
        return psiClass.isInterface();
      }
    });}
  public PsiClassPattern isAnnotationType() {
    return with(new PatternCondition<PsiClass>("isAnnotationType") {
      public boolean accepts(@NotNull final PsiClass psiClass, final ProcessingContext context) {
        return psiClass.isAnnotationType();
      }
    });}

  public PsiClassPattern withMethod(final boolean checkDeep, final ElementPattern<? extends PsiMethod> memberPattern) {
    return with(new PatternCondition<PsiClass>("withMethod") {
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
