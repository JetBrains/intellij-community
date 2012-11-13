package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
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
    Class<? extends PsiModifierListOwner>[] types = p.getTypes();

    for (PsiElement elt : getAnnotationCandidates(annClass, useScope)) {
      if (notAnnotation(elt)) continue;

      final PsiAnnotation ann = (PsiAnnotation)elt;
      final PsiJavaCodeReferenceElement ref = ApplicationManager.getApplication().runReadAction(new Computable<PsiJavaCodeReferenceElement>() {
        @Override
        public PsiJavaCodeReferenceElement compute() {
          return ann.getNameReferenceElement();
        }
      });
      if (ref == null) continue;

      PsiElement parent = ann.getParent();
      if (!(parent instanceof PsiModifierList)) continue; // Can be a PsiNameValuePair, if annotation is used to annotate annotation parameters

      final PsiElement owner = parent.getParent();

      if (!isInstanceof(owner, types)) continue;

      final PsiModifierListOwner candidate = (PsiModifierListOwner)owner;

      if (!ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          if (!candidate.isValid()) {
            return false;
          }
          if (!psiManager.areElementsEquivalent(ref.resolve(), annClass)) {
            return false;
          }
          return !(useScope instanceof GlobalSearchScope) ||
                 ((GlobalSearchScope)useScope).contains(candidate.getContainingFile().getVirtualFile());
        }
      })) {
        continue;
      }

      if (!consumer.process(candidate)) {
        return false;
      }
    }

    return true;
  }

  private static Collection<? extends PsiElement> getAnnotationCandidates(final PsiClass annClass, final SearchScope useScope) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Collection<? extends PsiElement>>() {
      @Override
      public Collection<? extends PsiElement> compute() {
        if (useScope instanceof GlobalSearchScope) {
          return JavaAnnotationIndex.getInstance().get(annClass.getName(), annClass.getProject(), (GlobalSearchScope)useScope);
        }
        final List<PsiElement> result = new ArrayList<PsiElement>();
        for (PsiElement element : ((LocalSearchScope)useScope).getScope()) {
          element.accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(PsiElement element) {
              if (element instanceof PsiAnnotation) {
                result.add(element);
              }
            }
          });
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

  private static boolean notAnnotation(final PsiElement found) {
    if (found instanceof PsiAnnotation) return false;

    VirtualFile faultyContainer = PsiUtilCore.getVirtualFile(found);
    LOG.error("Non annotation in annotations list: " + faultyContainer+"; element:"+found);
    if (faultyContainer != null && faultyContainer.isValid()) {
      FileBasedIndex.getInstance().requestReindex(faultyContainer);
    }

    return true;
  }
}
