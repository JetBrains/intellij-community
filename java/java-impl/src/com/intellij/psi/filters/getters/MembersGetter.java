/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ik
 * @author peter
 */
public abstract class MembersGetter {

  public void processMembers(@NotNull final PsiElement context, final Consumer<LookupElement> results, @Nullable final PsiClass where, final boolean acceptMethods) {
    if (where == null) return;

    PsiClass current = PsiTreeUtil.getContextOfType(context, PsiClass.class);
    while (current != null) {
      current = CompletionUtil.getOriginalOrSelf(current);
      if (InheritanceUtil.isInheritorOrSelf(current, where, true)) {
        return;
      }
      current = PsiTreeUtil.getContextOfType(current, PsiClass.class);
    }

    final FilterScopeProcessor<PsiElement> processor = new FilterScopeProcessor<PsiElement>(TrueFilter.INSTANCE);
    where.processDeclarations(processor, ResolveState.initial(), null, context);

    final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(context.getProject()).getResolveHelper();
    for (final PsiElement result : processor.getResults()) {
      if (result instanceof PsiMember && !(result instanceof PsiClass)) {
        final PsiMember member = (PsiMember)result;
        if (member.hasModifierProperty(PsiModifier.STATIC) && resolveHelper.isAccessible(member, context, null)) {
          if (result instanceof PsiField && !member.hasModifierProperty(PsiModifier.FINAL)) continue;
          if (result instanceof PsiMethod && acceptMethods) continue;
          final LookupElement item = result instanceof PsiMethod ? createMethodElement((PsiMethod)result) : createFieldElement((PsiField)result);
          if (item != null) {
            results.consume(AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(item));
          }
        }
      }
    }
  }

  @Nullable
  protected abstract LookupElement createFieldElement(PsiField field);

  @Nullable
  protected abstract LookupElement createMethodElement(PsiMethod method);
}
