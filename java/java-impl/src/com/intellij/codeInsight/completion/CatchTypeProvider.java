// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author peter
 */
final class CatchTypeProvider {
  static final ElementPattern<PsiElement> CATCH_CLAUSE_TYPE = psiElement().insideStarting(
    psiElement(PsiTypeElement.class).withParent(
      psiElement(PsiCatchSection.class)));

  static void addCompletions(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiTryStatement.class);
    final PsiCodeBlock tryBlock = tryStatement == null ? null : tryStatement.getTryBlock();
    if (tryBlock == null) return;

    final JavaCompletionSession session = new JavaCompletionSession(result);

    for (final PsiClassType type : ExceptionUtil.getThrownExceptions(tryBlock.getStatements())) {
      PsiClass typeClass = type.resolve();
      if (typeClass != null) {
        result.addElement(createCatchTypeVariant(tryBlock, type));
        session.registerClass(typeClass);
      }
    }

    final Collection<PsiClassType> expectedClassTypes = Collections.singletonList(JavaPsiFacade.getElementFactory(
      tryBlock.getProject()).createTypeByFQClassName(CommonClassNames.JAVA_LANG_THROWABLE));
    JavaInheritorsGetter.processInheritors(parameters, expectedClassTypes, result.getPrefixMatcher(), type -> {
      final PsiClass psiClass = type instanceof PsiClassType ? ((PsiClassType)type).resolve() : null;
      if (psiClass == null || psiClass instanceof PsiTypeParameter) return;

      if (!session.alreadyProcessed(psiClass)) {
        result.addElement(createCatchTypeVariant(tryBlock, (PsiClassType)type));
      }
    });
  }

  @NotNull
  private static LookupElement createCatchTypeVariant(PsiCodeBlock tryBlock, PsiClassType type) {
    return TailTypeDecorator.withTail(PsiTypeLookupItem.createLookupItem(type, tryBlock), TailType.HUMBLE_SPACE_BEFORE_WORD);
  }
}
