/*
 * @author max
 */
package com.intellij.psi.impl.search;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AnnotatedPackagesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class AnnotatedPackagesSearcher implements QueryExecutor<PsiPackage, AnnotatedPackagesSearch.Parameters> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.AnnotatedPackagesSearcher");

  public boolean execute(final AnnotatedPackagesSearch.Parameters p, final Processor<PsiPackage> consumer) {
    final PsiClass annClass = p.getAnnotationClass();
    assert annClass.isAnnotationType() : "Annotation type should be passed to annotated packages search";

    final String annotationFQN = annClass.getQualifiedName();
    assert annotationFQN != null;

    final PsiManagerImpl psiManager = (PsiManagerImpl)PsiManager.getInstance(annClass.getProject());
    final SearchScope useScope = p.getScope();

    final String annotationShortName = annClass.getName();
    assert annotationShortName != null;

    final GlobalSearchScope scope = useScope instanceof GlobalSearchScope ? (GlobalSearchScope)useScope : null;

    final Collection<PsiAnnotation> annotations = JavaAnnotationIndex.getInstance().get(annotationShortName, annClass.getProject(), scope);
    for (PsiAnnotation annotation : annotations) {
      PsiModifierList modlist = (PsiModifierList)annotation.getParent();
      final PsiElement owner = modlist.getParent();
      if (!(owner instanceof PsiClass)) continue;
      PsiClass candidate = (PsiClass)owner;
      if (!"package-info".equals(candidate.getName())) continue;

      LOG.assertTrue(candidate.isValid());

      final PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
      if (ref == null) continue;

      if (!psiManager.areElementsEquivalent(ref.resolve(), annClass)) continue;
      if (useScope instanceof GlobalSearchScope &&
          !((GlobalSearchScope)useScope).contains(candidate.getContainingFile().getVirtualFile())) {
        continue;
      }
      final String qname = candidate.getQualifiedName();
      if (qname != null && !consumer.process(JavaPsiFacade.getInstance(psiManager.getProject()).findPackage(
        qname.substring(0, qname.lastIndexOf('.'))))) {
        return false;
      }
    }

    PsiSearchHelper helper = psiManager.getSearchHelper();
    final GlobalSearchScope infoFilesFilter = new PackageInfoFilesOnly();

    GlobalSearchScope infoFiles =
      useScope instanceof GlobalSearchScope ? ((GlobalSearchScope)useScope).intersectWith(infoFilesFilter) : infoFilesFilter;

    final boolean[] wantmore = new boolean[]{true};
    helper.processAllFilesWithWord(annotationShortName, infoFiles, new Processor<PsiFile>() {
      public boolean process(final PsiFile psiFile) {
        PsiPackageStatement stmt = PsiTreeUtil.getChildOfType(psiFile, PsiPackageStatement.class);
        if (stmt == null) return true;

        final PsiModifierList annotations = stmt.getAnnotationList();
        if (annotations == null) return true;
        final PsiAnnotation ann = annotations.findAnnotation(annotationFQN);
        if (ann == null) return true;

        final PsiJavaCodeReferenceElement ref = ann.getNameReferenceElement();
        if (ref == null) return true;

        if (!psiManager.areElementsEquivalent(ref.resolve(), annClass)) return true;

        wantmore[0] = consumer.process(JavaPsiFacade.getInstance(psiManager.getProject()).findPackage(stmt.getPackageName()));
        return wantmore[0];
      }
    }, true);

    return wantmore[0];
  }

  private static class PackageInfoFilesOnly extends GlobalSearchScope {
    public int compare(final VirtualFile file1, final VirtualFile file2) {
      return 0;
    }

    public boolean contains(final VirtualFile file) {
      return "package-info.java".equals(file.getName());
    }

    public boolean isSearchInLibraries() {
      return false;
    }

    public boolean isSearchInModuleContent(@NotNull final Module aModule) {
      return true;
    }
  }
}