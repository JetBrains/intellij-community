// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.codeInsight.completion.JavaInheritorsGetter;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcompletion.ModCompletionItem;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.filters.getters.InstanceOfLeftPartTypeGetter;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;

@NotNullByDefault
final class InstanceOfClassItemProvider extends JavaModCompletionItemProvider {
  static final ElementPattern<PsiElement> AFTER_INSTANCEOF = psiElement().afterLeaf(JavaKeywords.INSTANCEOF);

  @Override
  public void provideItems(CompletionContext context, ModCompletionResult sink) {
    if (!context.isSmart()) return;
    final PsiElement position = context.element();
    if (!AFTER_INSTANCEOF.accepts(position)) return;
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
      .processInheritors(context, expectedClassTypes, context.matcher(), type -> {
        final PsiClass psiClass = PsiUtil.resolveClassInType(type);
        if (psiClass == null || psiClass instanceof PsiTypeParameter) return;

        if (expectedClassTypes.contains(type)) return;

        sink.accept(createInstanceofLookupElement(psiClass, parameterizedTypes));
      });
  }

  private static ModCompletionItem createInstanceofLookupElement(PsiClass psiClass, Set<? extends PsiClass> toWildcardInheritors) {
    final PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    if (typeParameters.length > 0) {
      for (final PsiClass parameterizedType : toWildcardInheritors) {
        if (psiClass.isInheritor(parameterizedType, true)) {
          final PsiWildcardType wildcard = PsiWildcardType.createUnbounded(psiClass.getManager());
          for (final PsiTypeParameter typeParameter : typeParameters) {
            substitutor = substitutor.put(typeParameter, wildcard);
          }
          break;
        }
      }
    }

    PsiClassType type = factory.createType(psiClass, substitutor);
    return PsiTypeCompletionItem.create(type).showPackage();
  }
}
