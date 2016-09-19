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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author max
 */
public class AnnotatedElementsSearcher implements QueryExecutor<PsiModifierListOwner, AnnotatedElementsSearch.Parameters> {
  @Override
  public boolean execute(@NotNull final AnnotatedElementsSearch.Parameters p, @NotNull final Processor<PsiModifierListOwner> consumer) {
    final PsiClass annClass = p.getAnnotationClass();
    if (!annClass.isAnnotationType()) throw new IllegalArgumentException("Annotation type should be passed to annotated members search but got: "+annClass);

    String annotationFQN = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return annClass.getQualifiedName();
      }
    });
    if (annotationFQN == null) throw new IllegalArgumentException("FQN is null for "+annClass);

    final PsiManager psiManager = ApplicationManager.getApplication().runReadAction(new Computable<PsiManager>() {
      @Override
      public PsiManager compute() {
        return annClass.getManager();
      }
    });

    final SearchScope useScope = p.getScope();
    final Class<? extends PsiModifierListOwner>[] types = p.getTypes();

    for (final PsiAnnotation ann : getAnnotationCandidates(annClass, useScope, psiManager.getProject())) {
      final PsiModifierListOwner candidate = ApplicationManager.getApplication().runReadAction(new Computable<PsiModifierListOwner>() {
        @Override
        public PsiModifierListOwner compute() {
          PsiElement parent = ann.getContext();
          if (!(parent instanceof PsiModifierList)) {
            return null; // Can be a PsiNameValuePair, if annotation is used to annotate annotation parameters
          }

          final PsiElement owner = parent.getParent();
          if (!isInstanceof(owner, types)) {
            return null;
          }

          if (p.isApproximate()) {
            return (PsiModifierListOwner)owner;
          }

          final PsiJavaCodeReferenceElement ref = ann.getNameReferenceElement();
          if (ref == null || !psiManager.areElementsEquivalent(ref.resolve(), annClass)) {
            return null;
          }

          return (PsiModifierListOwner)owner;
        }
      });

      if (candidate != null && !consumer.process(candidate)) {
        return false;
      }
    }

    return true;
  }

  @NotNull
  private static Collection<PsiAnnotation> getAnnotationCandidates(@NotNull PsiClass annClass,
                                                                   @NotNull SearchScope useScope, @NotNull Project project) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Collection<PsiAnnotation>>() {
      @Override
      public Collection<PsiAnnotation> compute() {
        if (useScope instanceof GlobalSearchScope) {
          return JavaAnnotationIndex.getInstance().get(annClass.getName(), project, (GlobalSearchScope)useScope);
        }

        List<PsiAnnotation> result = new ArrayList<>();
        for (PsiElement element : ((LocalSearchScope)useScope).getScope()) {
          result.addAll(PsiTreeUtil.findChildrenOfType(element, PsiAnnotation.class));
        }
        return result;
      }
    });
  }

  public static boolean isInstanceof(PsiElement owner, @NotNull Class<? extends PsiModifierListOwner>[] types) {
    for (Class<? extends PsiModifierListOwner> type : types) {
        if(type.isInstance(owner)) return true;
    }
    return false;
  }

}
