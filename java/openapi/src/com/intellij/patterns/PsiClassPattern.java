/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.util.ProcessingContext;
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
      public boolean accepts(@NotNull PsiClass psiClass, final ProcessingContext context) {
        return isInheritor(strict ? psiClass.getSuperClass() : psiClass, pattern, context);
      }
    });
  }

  private static boolean isInheritor(PsiClass psiClass, ElementPattern pattern, final ProcessingContext matchingContext) {
    if (psiClass == null) return false;
    if (pattern.getCondition().accepts(psiClass, matchingContext)) return true;
    if (isInheritor(psiClass.getSuperClass(), pattern, matchingContext)) return true;
    for (final PsiClass aClass : psiClass.getInterfaces()) {
      if (isInheritor(aClass, pattern, matchingContext)) return true;
    }
    return false;
  }


  public PsiClassPattern inheritorOf(final boolean strict, final String className) {
    return inheritorOf(strict, PsiJavaPatterns.psiClass().withQualifiedName(className));
  }

  public PsiClassPattern isInterface() {
    return with(new PatternCondition<PsiClass>("isInterface") {
      public boolean accepts(@NotNull final PsiClass psiClass, final ProcessingContext context) {
        return psiClass.isInterface();
      }
    });}

  public PsiClassPattern withMember(final ElementPattern<? extends PsiMember> memberPattern) {
    return with(new PatternCondition<PsiClass>("withMember") {
      public boolean accepts(@NotNull final PsiClass psiClass, final ProcessingContext context) {
        for (PsiMethod method : psiClass.getMethods()) {
          if (memberPattern.accepts(method, context)) {
            return true;
          }
        }
        for (PsiField field : psiClass.getFields()) {
          if (memberPattern.accepts(field, context)) {
            return true;
          }
        }
        for (PsiClass inner : psiClass.getInnerClasses()) {
          if (memberPattern.accepts(inner, context)) {
            return true;
          }
        }
        return false;
      }
    });}

   public PsiClassPattern nonAnnotationType() {
    return with(new PatternCondition<PsiClass>("nonAnnotationType") {
      public boolean accepts(@NotNull final PsiClass psiClass, final ProcessingContext context) {
        return !psiClass.isAnnotationType();
      }
    });
  }

  public PsiClassPattern withQualifiedName(@NonNls @NotNull final String qname) {
    return with(new PatternCondition<PsiClass>("withQualifiedName") {
      public boolean accepts(@NotNull final PsiClass psiClass, final ProcessingContext context) {
        return qname.equals(psiClass.getQualifiedName());
      }
    });
  }
  

}
