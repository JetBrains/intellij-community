package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author max
 */
public class AnnotatedElementsSearcher implements QueryExecutor<PsiModifierListOwner, AnnotatedElementsSearch.Parameters> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.AnnotatedMembersSearcher");

  @Override
  public boolean execute(@NotNull final AnnotatedElementsSearch.Parameters p, @NotNull final Processor<PsiModifierListOwner> consumer) {
    final PsiClass annClass = p.getAnnotationClass();
    assert annClass.isAnnotationType() : "Annotation type should be passed to annotated members search";

    final String annotationFQN = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return annClass.getQualifiedName();
      }
    });
    assert annotationFQN != null;

    final PsiManagerImpl psiManager = (PsiManagerImpl)annClass.getManager();

    final SearchScope useScope = p.getScope();
    final Class<? extends PsiModifierListOwner>[] types = p.getTypes();

    for (final PsiAnnotation ann : getAnnotationCandidates(annClass, useScope)) {
      final PsiModifierListOwner candidate = ApplicationManager.getApplication().runReadAction(new Computable<PsiModifierListOwner>() {
        @Override
        public PsiModifierListOwner compute() {
          PsiElement parent = ann.getParent();
          if (!(parent instanceof PsiModifierList)) {
            return null; // Can be a PsiNameValuePair, if annotation is used to annotate annotation parameters
          }

          final PsiElement owner = parent.getParent();
          if (!isInstanceof(owner, types)) {
            return null;
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

  private static Collection<PsiAnnotation> getAnnotationCandidates(final PsiClass annClass, final SearchScope useScope) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Collection<PsiAnnotation>>() {
      @Override
      public Collection<PsiAnnotation> compute() {
        if (useScope instanceof GlobalSearchScope) {
          return JavaAnnotationIndex.getInstance().get(annClass.getName(), annClass.getProject(), (GlobalSearchScope)useScope);
        }

        final List<PsiAnnotation> result = ContainerUtil.newArrayList();
        for (PsiElement element : ((LocalSearchScope)useScope).getScope()) {
          result.addAll(PsiTreeUtil.findChildrenOfType(element, PsiAnnotation.class));
        }
        return result;
      }
    });
  }

  public static boolean isInstanceof(PsiElement owner, Class<? extends PsiModifierListOwner>[] types) {
    for (Class<? extends PsiModifierListOwner> type : types) {
        if(type.isInstance(owner)) return true;
    }
    return false;
  }

}
