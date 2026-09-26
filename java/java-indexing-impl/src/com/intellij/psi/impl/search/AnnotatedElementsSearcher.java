// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class AnnotatedElementsSearcher implements QueryExecutor<PsiModifierListOwner, AnnotatedElementsSearch.Parameters> {
  @Override
  public boolean execute(final @NotNull AnnotatedElementsSearch.Parameters p, final @NotNull Processor<? super PsiModifierListOwner> consumer) {
    Project project = p.getProject();
    PsiClass annotationClass = p.getAnnotationClass();
    String annotationFQN = getAnnotationName(p);
    if (annotationFQN == null) throw new IllegalArgumentException("FQN is null for " + annotationClass);

    SearchScope useScope = p.getScope();
    Class<? extends PsiModifierListOwner>[] types = p.getTypes();
    String shortName = StringUtil.getShortName(annotationFQN);

    for (final PsiAnnotation ann : getAnnotationCandidates(shortName, useScope, project)) {
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
        
        boolean match = annotationClass == null ? ann.hasQualifiedName(annotationFQN) :
                        annotationClass.isEquivalentTo(ann.resolveAnnotationType());
        if (!match) return null;

        return (PsiModifierListOwner)owner;
      });

      if (candidate != null && !consumer.process(candidate)) {
        return false;
      }
    }

    return true;
  }

  private static @Nullable String getAnnotationName(AnnotatedElementsSearch.@NotNull Parameters p) {
    String name = p.getAnnotationName();
    if (name != null) return name;
    return ReadAction.compute(() -> {
      PsiClass annClass = p.getAnnotationClass();
      if (annClass != null && !annClass.isAnnotationType()) {
        throw new IllegalArgumentException("Annotation type should be passed to annotated members search but got: " + annClass);
      }
      return annClass == null ? null : annClass.getQualifiedName();
    });
  }

  private static @NotNull @Unmodifiable Collection<PsiAnnotation> getAnnotationCandidates(@NotNull String shortName,
                                                                                          @NotNull SearchScope useScope, 
                                                                                          @NotNull Project project) {
    return ReadAction.compute(() -> {
      if (useScope instanceof GlobalSearchScope) {
        return JavaAnnotationIndex.getInstance().getAnnotations(shortName, project, (GlobalSearchScope)useScope);
      }

      List<PsiAnnotation> result = new ArrayList<>();
      for (PsiElement element : ((LocalSearchScope)useScope).getScope()) {
        result.addAll(PsiTreeUtil.findChildrenOfType(element, PsiAnnotation.class));
      }
      return result;
    });
  }

  public static boolean isInstanceof(PsiElement owner, Class<? extends PsiModifierListOwner> @NotNull [] types) {
    for (Class<? extends PsiModifierListOwner> type : types) {
        if(type.isInstance(owner)) return true;
    }
    return false;
  }

}
