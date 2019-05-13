// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
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
  public boolean execute(@NotNull final AnnotatedElementsSearch.Parameters p, @NotNull final Processor<? super PsiModifierListOwner> consumer) {
    final PsiClass annClass = p.getAnnotationClass();
    if (!annClass.isAnnotationType()) throw new IllegalArgumentException("Annotation type should be passed to annotated members search but got: "+annClass);

    String annotationFQN = ReadAction.compute(() -> annClass.getQualifiedName());
    if (annotationFQN == null) throw new IllegalArgumentException("FQN is null for "+annClass);

    final PsiManager psiManager = ReadAction.compute(() -> annClass.getManager());

    final SearchScope useScope = p.getScope();
    final Class<? extends PsiModifierListOwner>[] types = p.getTypes();

    for (final PsiAnnotation ann : getAnnotationCandidates(annClass, useScope, psiManager.getProject())) {
      final PsiModifierListOwner candidate = ReadAction.compute(() -> {
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
    return ReadAction.compute(() -> {
      if (useScope instanceof GlobalSearchScope) {
        return JavaAnnotationIndex.getInstance().get(annClass.getName(), project, (GlobalSearchScope)useScope);
      }

      List<PsiAnnotation> result = new ArrayList<>();
      for (PsiElement element : ((LocalSearchScope)useScope).getScope()) {
        result.addAll(PsiTreeUtil.findChildrenOfType(element, PsiAnnotation.class));
      }
      return result;
    });
  }

  public static boolean isInstanceof(PsiElement owner, @NotNull Class<? extends PsiModifierListOwner>[] types) {
    for (Class<? extends PsiModifierListOwner> type : types) {
        if(type.isInstance(owner)) return true;
    }
    return false;
  }

}
