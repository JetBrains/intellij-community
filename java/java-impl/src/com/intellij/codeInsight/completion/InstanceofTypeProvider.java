// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.filters.getters.InstanceOfLeftPartTypeGetter;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;

final class InstanceofTypeProvider {
  static final ElementPattern<PsiElement> AFTER_INSTANCEOF = psiElement().afterLeaf(JavaKeywords.INSTANCEOF);

  static void addCompletions(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    final PsiElement position = parameters.getPosition();
    final PsiType[] leftTypes = InstanceOfLeftPartTypeGetter.getLeftTypes(position);
    final Set<PsiClassType> expectedClassTypes = new LinkedHashSet<>();
    final Set<PsiClass> parameterizedTypes = new HashSet<>();
    for (final PsiType type : leftTypes) {
      if (type instanceof PsiClassType classType) {
        if (!classType.isRaw()) {
          ContainerUtil.addIfNotNull(parameterizedTypes, classType.resolve());
        }

        expectedClassTypes.add(classType.rawType());
      }
    }

    JavaInheritorsGetter
      .processInheritors(parameters, expectedClassTypes, result.getPrefixMatcher(), type -> {
        final PsiClass psiClass = PsiUtil.resolveClassInType(type);
        if (psiClass == null || psiClass instanceof PsiTypeParameter) return;

        if (expectedClassTypes.contains(type)) return;

        result.addElement(createInstanceofLookupElement(psiClass, parameterizedTypes));
      });
  }

  private static LookupElement createInstanceofLookupElement(PsiClass psiClass, Set<? extends PsiClass> toWildcardInheritors) {
    final PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
    if (typeParameters.length > 0) {
      for (final PsiClass parameterizedType : toWildcardInheritors) {
        if (psiClass.isInheritor(parameterizedType, true)) {
          PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
          final PsiWildcardType wildcard = PsiWildcardType.createUnbounded(psiClass.getManager());
          for (final PsiTypeParameter typeParameter : typeParameters) {
            substitutor = substitutor.put(typeParameter, wildcard);
          }
          final PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
          return PsiTypeLookupItem.createLookupItem(factory.createType(psiClass, substitutor), psiClass);
        }
      }
    }

    return new JavaPsiClassReferenceElement(psiClass);
  }
}
