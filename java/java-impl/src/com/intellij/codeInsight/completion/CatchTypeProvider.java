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

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author peter
 */
class CatchTypeProvider extends CompletionProvider<CompletionParameters> {
  static final ElementPattern<PsiElement> CATCH_CLAUSE_TYPE = psiElement().insideStarting(
    psiElement(PsiTypeElement.class).withParent(
      psiElement(PsiCatchSection.class)));

  @Override
  protected void addCompletions(@NotNull final CompletionParameters parameters,
                                final ProcessingContext context,
                                @NotNull final CompletionResultSet result) {
    PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiTryStatement.class);
    final PsiCodeBlock tryBlock = tryStatement == null ? null : tryStatement.getTryBlock();
    if (tryBlock == null) return;

    final InheritorsHolder holder = new InheritorsHolder(result);

    for (final PsiClassType type : ExceptionUtil.getThrownExceptions(tryBlock.getStatements())) {
      PsiClass typeClass = type.resolve();
      if (typeClass != null) {
        result.addElement(createCatchTypeVariant(tryBlock, type));
        holder.registerClass(typeClass);
      }
    }

    final Collection<PsiClassType> expectedClassTypes = Collections.singletonList(JavaPsiFacade.getElementFactory(
      tryBlock.getProject()).createTypeByFQClassName(CommonClassNames.JAVA_LANG_THROWABLE));
    JavaInheritorsGetter.processInheritors(parameters, expectedClassTypes, result.getPrefixMatcher(), new Consumer<PsiType>() {
      @Override
      public void consume(PsiType type) {
        final PsiClass psiClass = type instanceof PsiClassType ? ((PsiClassType)type).resolve() : null;
        if (psiClass == null || psiClass instanceof PsiTypeParameter) return;

        if (!holder.alreadyProcessed(psiClass)) {
          result.addElement(createCatchTypeVariant(tryBlock, (PsiClassType)type));
        }
      }
    });
  }

  @NotNull
  private static LookupElement createCatchTypeVariant(PsiCodeBlock tryBlock, PsiClassType type) {
    return TailTypeDecorator.withTail(PsiTypeLookupItem.createLookupItem(type, tryBlock), TailType.HUMBLE_SPACE_BEFORE_WORD);
  }
}
