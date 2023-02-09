// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

final class MethodReturnTypeProvider {
  static final ElementPattern<PsiElement> IN_METHOD_RETURN_TYPE =
    psiElement().withParents(PsiJavaCodeReferenceElement.class, PsiTypeElement.class, PsiMethod.class)
      .andNot(JavaKeywordCompletion.AFTER_DOT);

  static void addProbableReturnTypes(@NotNull PsiElement position, Consumer<? super LookupElement> consumer) {
    PsiMethod method = PsiTreeUtil.getParentOfType(position, PsiMethod.class);
    assert method != null;

    final PsiTypeVisitor<PsiType> eachProcessor = new PsiTypeVisitor<>() {
      private final Set<PsiType> myProcessed = new HashSet<>();

      @Override
      public PsiType visitType(@NotNull PsiType type) {
        if (!(type instanceof PsiPrimitiveType) && PsiTypesUtil.isDenotableType(type, position) && myProcessed.add(type)) {
          int priority = type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ? 1 : 1000 - myProcessed.size();
          consumer.consume(PrioritizedLookupElement.withPriority(PsiTypeLookupItem.createLookupItem(type, position), priority));
        }
        return type;
      }
    };
    for (PsiType type : getReturnTypeCandidates(method)) {
      eachProcessor.visitType(type);
      ExpectedTypesProvider.processAllSuperTypes(type, eachProcessor, position.getProject(), new HashSet<>(), new HashSet<>());
    }
  }

  private static PsiType[] getReturnTypeCandidates(@NotNull PsiMethod method) {
    PsiType lub = null;
    boolean hasVoid = false;
    for (PsiReturnStatement statement : PsiUtil.findReturnStatements(method)) {
      PsiExpression value = statement.getReturnValue();
      if (value == null) {
        hasVoid = true;
      }
      else {
        PsiType type = value.getType();
        if (lub == null) {
          lub = type;
        }
        else if (type != null) {
          lub = GenericsUtil.getLeastUpperBound(lub, type, method.getManager());
        }
      }
    }
    if (hasVoid && lub == null) {
      lub = PsiTypes.voidType();
    }
    if (lub instanceof PsiIntersectionType) {
      return ((PsiIntersectionType)lub).getConjuncts();
    }
    return lub == null ? PsiType.EMPTY_ARRAY : new PsiType[]{lub};
  }
}
