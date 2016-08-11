/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.filters.getters.InstanceOfLeftPartTypeGetter;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author peter
 */
class InstanceofTypeProvider extends CompletionProvider<CompletionParameters> {
  static final ElementPattern<PsiElement> AFTER_INSTANCEOF = psiElement().afterLeaf(PsiKeyword.INSTANCEOF);

  @Override
  protected void addCompletions(@NotNull final CompletionParameters parameters,
                                final ProcessingContext context,
                                @NotNull final CompletionResultSet result) {
    final PsiElement position = parameters.getPosition();
    final PsiType[] leftTypes = InstanceOfLeftPartTypeGetter.getLeftTypes(position);
    final Set<PsiClassType> expectedClassTypes = new LinkedHashSet<>();
    final Set<PsiClass> parameterizedTypes = new THashSet<>();
    for (final PsiType type : leftTypes) {
      if (type instanceof PsiClassType) {
        final PsiClassType classType = (PsiClassType)type;
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

        //noinspection SuspiciousMethodCalls
        if (expectedClassTypes.contains(type)) return;

        result.addElement(createInstanceofLookupElement(psiClass, parameterizedTypes));
      });
  }

  private static LookupElement createInstanceofLookupElement(PsiClass psiClass, Set<PsiClass> toWildcardInheritors) {
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
