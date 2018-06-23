// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.AnnotatedPackagesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class AnnotatedPackagesSearcher implements QueryExecutor<PsiPackage, AnnotatedPackagesSearch.Parameters> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.AnnotatedPackagesSearcher");

  @Override
  public boolean execute(@NotNull final AnnotatedPackagesSearch.Parameters p, @NotNull final Processor<? super PsiPackage> consumer) {
    final PsiClass annClass = p.getAnnotationClass();
    assert annClass.isAnnotationType() : "Annotation type should be passed to annotated packages search";

    final String annotationFQN = ReadAction.compute(() -> annClass.getQualifiedName());
    assert annotationFQN != null;

    final PsiManager psiManager = ReadAction.compute(() -> annClass.getManager());
    final GlobalSearchScope useScope = (GlobalSearchScope)p.getScope();

    final String annotationShortName = ReadAction.compute(() -> annClass.getName());
    assert annotationShortName != null;

    final Collection<PsiAnnotation> annotations = JavaAnnotationIndex.getInstance().get(annotationShortName, psiManager.getProject(),
                                                                                        useScope);

    for (final PsiAnnotation annotation : annotations) {
      boolean accepted = ReadAction.compute(() -> {
        PsiModifierList modList = (PsiModifierList)annotation.getParent();
        final PsiElement owner = modList.getParent();
        if (owner instanceof PsiClass) {
          PsiClass candidate = (PsiClass)owner;
          if ("package-info".equals(candidate.getName())) {
            LOG.assertTrue(candidate.isValid());
            final PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
            if (ref != null && psiManager.areElementsEquivalent(ref.resolve(), annClass) &&
                useScope.contains(candidate.getContainingFile().getVirtualFile())) {
              final String qname = candidate.getQualifiedName();
              if (qname != null && !consumer.process(JavaPsiFacade.getInstance(psiManager.getProject()).findPackage(
                qname.substring(0, qname.lastIndexOf('.'))))) {
                return false;
              }
            }
          }
        }
        return true;
      });
      if (!accepted) return false;
    }

    PsiSearchHelper helper = PsiSearchHelper.getInstance(psiManager.getProject());
    final GlobalSearchScope infoFilesFilter = new PackageInfoFilesOnly();

    GlobalSearchScope infoFiles =
      useScope.intersectWith(infoFilesFilter);

    final boolean[] wantMore = {true};
    helper.processAllFilesWithWord(annotationShortName, infoFiles, psiFile -> {
      PsiPackageStatement stmt = PsiTreeUtil.getChildOfType(psiFile, PsiPackageStatement.class);
      if (stmt == null) return true;

      final PsiModifierList annotations1 = stmt.getAnnotationList();
      if (annotations1 == null) return true;
      final PsiAnnotation ann = annotations1.findAnnotation(annotationFQN);
      if (ann == null) return true;

      final PsiJavaCodeReferenceElement ref = ann.getNameReferenceElement();
      if (ref == null) return true;

      if (!psiManager.areElementsEquivalent(ref.resolve(), annClass)) return true;

      wantMore[0] = consumer.process(JavaPsiFacade.getInstance(psiManager.getProject()).findPackage(stmt.getPackageName()));
      return wantMore[0];
    }, true);

    return wantMore[0];
  }

  private static class PackageInfoFilesOnly extends GlobalSearchScope {
    @Override
    public int compare(@NotNull final VirtualFile file1, @NotNull final VirtualFile file2) {
      return 0;
    }

    @Override
    public boolean contains(@NotNull final VirtualFile file) {
      return "package-info.java".equals(file.getName());
    }

    @Override
    public boolean isSearchInLibraries() {
      return false;
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull final Module aModule) {
      return true;
    }
  }
}