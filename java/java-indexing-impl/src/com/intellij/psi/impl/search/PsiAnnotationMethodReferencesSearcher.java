/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import com.intellij.openapi.application.ReadActionProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class PsiAnnotationMethodReferencesSearcher implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final ReferencesSearch.SearchParameters p, @NotNull final Processor<PsiReference> consumer) {
    final PsiElement refElement = p.getElementToSearch();
    boolean isAnnotation = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return PsiUtil.isAnnotationMethod(refElement);
      }
    });
    if (isAnnotation) {
      final PsiMethod method = (PsiMethod)refElement;
      PsiClass containingClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
        @Override
        public PsiClass compute() {
          boolean isValueMethod =
            PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(method.getName()) && method.getParameterList().getParametersCount() == 0;
          return isValueMethod ? method.getContainingClass() : null;
        }
      });
      if (containingClass != null) {
        SearchScope scope = ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
          @Override
          public SearchScope compute() {
            return p.getEffectiveSearchScope();
          }
        });
        final Query<PsiReference> query = ReferencesSearch.search(containingClass, scope, p.isIgnoreAccessScope());
        return query.forEach(createImplicitDefaultAnnotationMethodConsumer(consumer));
      }
    }

    return true;
  }

  public static ReadActionProcessor<PsiReference> createImplicitDefaultAnnotationMethodConsumer(final Processor<PsiReference> consumer) {
    return new ReadActionProcessor<PsiReference>() {
      @Override
      public boolean processInReadAction(final PsiReference reference) {
        if (reference instanceof PsiJavaCodeReferenceElement) {
          PsiJavaCodeReferenceElement javaReference = (PsiJavaCodeReferenceElement)reference;
          if (javaReference.getParent() instanceof PsiAnnotation) {
            PsiNameValuePair[] members = ((PsiAnnotation)javaReference.getParent()).getParameterList().getAttributes();
            if (members.length == 1 && members[0].getNameIdentifier() == null) {
              PsiReference t = members[0].getReference();
              if (t != null && !consumer.process(t)) return false;
            }
          }
        }
        return true;
      }
    };
  }
}
