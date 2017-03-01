/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.compiler.CompilerReferenceService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.LongAdder;

class PreferMostUsedWeigher extends LookupElementWeigher {
  private final CompilerReferenceService myCompilerReferenceService;
  private final boolean myConstructorSuggestion;

  private PreferMostUsedWeigher(@NotNull CompilerReferenceService service, boolean constructorSuggestion) {
    super("mostUsed");
    myCompilerReferenceService = service;
    myConstructorSuggestion = constructorSuggestion;
  }

  // optimization: do not event create weigher if compiler indices aren't available for now
  @Nullable
  static PreferMostUsedWeigher create(@NotNull PsiElement position) {
    final CompilerReferenceService service = CompilerReferenceService.getInstance(position.getProject());
    return service.isActive() ? new PreferMostUsedWeigher(service, JavaSmartCompletionContributor.AFTER_NEW.accepts(position)) : null;
  }

  final static LongAdder l = new LongAdder();

  @Nullable
  @Override
  public Integer weigh(@NotNull LookupElement element) {
    final long ms = System.currentTimeMillis();
    final PsiElement psi = ObjectUtils.tryCast(element.getObject(), PsiElement.class);
    final Integer res;
    if (psi == null || !(psi.isPhysical())) {
      res = null;
    }
    else {
      if (myConstructorSuggestion && psi instanceof PsiClass) {
        int _res = 0;
        boolean add = true;
        for (PsiMethod method : ((PsiClass)psi).getConstructors()) {
          final Integer count = myCompilerReferenceService.getCompileTimeOccurrenceCount(method);
          if (count != null) {
            _res += count;
          } else {
            add = false;
            break;
          }
        }
        if (add) {
          res = _res;
        } else {
          res = null;
        }
      }
      else {
        res = myCompilerReferenceService.getCompileTimeOccurrenceCount(psi);
      }
    }
    l.add(System.currentTimeMillis() - ms);
    System.out.println("overhead " + l.longValue() + " for " + element);
    return res;
  }
}
