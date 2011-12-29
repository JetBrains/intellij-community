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

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.completion.StaticMemberProcessor;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author ik
 * @author peter
 */
public abstract class MembersGetter {

  public void processMembers(@NotNull final PsiElement context, final Consumer<LookupElement> results, @Nullable final PsiClass where,
                             final boolean acceptMethods, boolean searchInheritors,
                             StaticMemberProcessor processor) {
    if (where == null) return;
    
    final List<PsiClass> placeClasses = new ArrayList<PsiClass>();
    
    PsiClass current = PsiTreeUtil.getContextOfType(context, PsiClass.class);
    while (current != null) {
      current = CompletionUtil.getOriginalOrSelf(current);
      placeClasses.add(current);
      current = PsiTreeUtil.getContextOfType(current, PsiClass.class);
    }

    final Set<PsiMember> importedStatically = new HashSet<PsiMember>();
    processor.processMembersOfRegisteredClasses(null, new PairConsumer<PsiMember, PsiClass>() {
      @Override
      public void consume(PsiMember member, PsiClass psiClass) {
        importedStatically.add(member);
      }
    });
    
    final Condition<PsiClass> mayProcessMembers = new Condition<PsiClass>() {
      @Override
      public boolean value(PsiClass psiClass) {
        if (psiClass == null) {
          return false;
        }

        psiClass = CompletionUtil.getOriginalOrSelf(psiClass);
        for (PsiClass placeClass : placeClasses) {
          if (InheritanceUtil.isInheritorOrSelf(placeClass, psiClass, true)) {
            return false;
          }
        }
        return true;
      }
    };
    
    final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(context.getProject()).getResolveHelper();

    PsiClassType baseType = JavaPsiFacade.getElementFactory(where.getProject()).createType(where);
    Consumer<PsiType> consumer = new Consumer<PsiType>() {
      @Override
      public void consume(PsiType psiType) {
        PsiClass psiClass = PsiUtil.resolveClassInType(psiType);
        if (mayProcessMembers.value(psiClass)) {
          psiClass = CompletionUtil.getOriginalOrSelf(psiClass);
          for (PsiClass placeClass : placeClasses) {
            if (InheritanceUtil.isInheritorOrSelf(placeClass, psiClass, true)) {
              return;
            }
          }
          processClassDeclaredMembers(psiClass, context, acceptMethods, results, resolveHelper, importedStatically);
        }
      }
    };
    consumer.consume(baseType);
    if (searchInheritors && !CommonClassNames.JAVA_LANG_OBJECT.equals(where.getQualifiedName())) {
      CodeInsightUtil.processSubTypes(baseType, context, true, Condition.TRUE, consumer);
    }
  }

  private void processClassDeclaredMembers(PsiClass where,
                                           PsiElement context,
                                           boolean acceptMethods,
                                           Consumer<LookupElement> results, final PsiResolveHelper resolveHelper, final Set<PsiMember> importedStatically) {
    final FilterScopeProcessor<PsiElement> processor = new FilterScopeProcessor<PsiElement>(TrueFilter.INSTANCE);
    where.processDeclarations(processor, ResolveState.initial(), null, context);

    for (final PsiElement result : processor.getResults()) {
      if (result instanceof PsiMember && !(result instanceof PsiClass)) {
        final PsiMember member = (PsiMember)result;
        if (JavaCompletionUtil.isInExcludedPackage(member) || importedStatically.contains(member)) continue;
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
