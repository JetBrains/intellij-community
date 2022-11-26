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
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.patterns.PlatformPatterns.psiElement;

final class CatchTypeProvider {
  static final ElementPattern<PsiElement> CATCH_CLAUSE_TYPE = psiElement().insideStarting(
    psiElement(PsiTypeElement.class).withParent(
      psiElement(PsiCatchSection.class)));

  static void addCompletions(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiTryStatement.class);
    final PsiCodeBlock tryBlock = tryStatement == null ? null : tryStatement.getTryBlock();
    if (tryBlock == null) return;

    final JavaCompletionSession session = new JavaCompletionSession(result);

    List<PsiClass> preferred = new ArrayList<>();
    for (final PsiClassType type : ExceptionUtil.getThrownExceptions(tryBlock.getStatements())) {
      PsiClass typeClass = type.resolve();
      if (typeClass != null) {
        result.addElement(PrioritizedLookupElement.withPriority(createCatchTypeVariant(tryBlock, type), 100));
        session.registerClass(typeClass);
        preferred.add(typeClass);
      }
    }

    final Collection<PsiClassType> expectedClassTypes = Collections.singletonList(JavaPsiFacade.getElementFactory(
      tryBlock.getProject()).createTypeByFQClassName(CommonClassNames.JAVA_LANG_THROWABLE));
    JavaInheritorsGetter.processInheritors(parameters, expectedClassTypes, result.getPrefixMatcher(), type -> {
      final PsiClass psiClass = type instanceof PsiClassType ? ((PsiClassType)type).resolve() : null;
      if (psiClass == null || psiClass instanceof PsiTypeParameter) return;

      if (!session.alreadyProcessed(psiClass)) {
        LookupElement element = createCatchTypeVariant(tryBlock, (PsiClassType)type);
        final int maxNumberOfHopsToConsider = 25;
        // Tune priorities for classes in catch:
        // 100 = class that exactly matches the thrown type
        // 26..50 = superclasses of the thrown type (higher priority = less inheritance hops)
        // 1..25 = subclasses of the thrown type (higher priority = less inheritance hops)
        int priority = StreamEx.of(preferred)
          .mapToInt(aClass -> {
            int hops = Math.min(maxNumberOfHopsToConsider, getNumberOfHops(psiClass, aClass));
            if (hops >= 0) {
              return 1 + maxNumberOfHopsToConsider * 2 - hops;
            }
            hops = Math.min(maxNumberOfHopsToConsider, getNumberOfHops(aClass, psiClass));
            if (hops >= 0) {
              return 1 + maxNumberOfHopsToConsider - hops;
            }
            return 0;
          })
          .max().orElse(0);
        if (priority > 0) {
          element = PrioritizedLookupElement.withPriority(element, priority);
        }
        result.addElement(element);
      }
    });
  }

  /**
   * @return number of hops between super-class and sub-class; -1 if superClass is not actually a superClass of subclass
   * Works for classes only, not for interfaces
   */
  private static int getNumberOfHops(@NotNull PsiClass superClass, @NotNull PsiClass subClass) {
    int numberOfHops = 0;
    while (true) {
      if (subClass.equals(superClass)) return numberOfHops;
      PsiClass next = subClass.getSuperClass();
      if (next == null) return -1;
      numberOfHops++;
      subClass = next;
    }
  }

  @NotNull
  private static LookupElement createCatchTypeVariant(PsiCodeBlock tryBlock, PsiClassType type) {
    return TailTypeDecorator.withTail(PsiTypeLookupItem.createLookupItem(type, tryBlock), TailType.HUMBLE_SPACE_BEFORE_WORD);
  }
}
