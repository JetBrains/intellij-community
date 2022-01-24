// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.migration;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class MigrationUtil {
  private static final Logger LOG = Logger.getInstance(MigrationUtil.class);

  private MigrationUtil() {
  }

  public static UsageInfo[] findPackageUsages(Project project, PsiMigration migration, String qName, GlobalSearchScope searchScope) {
    PsiPackage aPackage = findOrCreatePackage(project, migration, qName);

    return findRefs(aPackage, searchScope).toArray(UsageInfo.EMPTY_ARRAY);
  }

  private static @Nullable PsiElement bindNonJavaReference(PsiElement bindTo, PsiElement element, UsageInfo usage) {
    if (element instanceof PsiFile) return null; // rename of files is not supported yet, IDEA-272542

    final TextRange range = usage.getRangeInElement();
    for (PsiReference reference : element.getReferences()) {
      if (reference instanceof JavaClassReference) {
        final JavaClassReference classReference = (JavaClassReference)reference;
        if (classReference.getRangeInElement().equals(range)) {
          return classReference.bindToElement(bindTo);
        }
      }
    }
    return bindTo;
  }

  public static UsageInfo[] findClassUsages(Project project, PsiMigration migration, String qName, GlobalSearchScope searchScope) {
    PsiClass[] classes = findOrCreateClass(project, migration, qName);

    List<UsageInfo> usages = new ArrayList<>();
    for (PsiClass aClass : classes) {

      usages.addAll(findRefs(aClass, searchScope));
    }
    return usages.toArray(UsageInfo.EMPTY_ARRAY);
  }

  private static List<UsageInfo> findRefs(final PsiElement aClass, GlobalSearchScope searchScope) {
    List<UsageInfo> results = new ArrayList<>();
    for (PsiReference usage : ReferencesSearch.search(aClass, searchScope, false)) {
      results.add(new UsageInfo(usage));
    }

    results.sort(Comparator.<UsageInfo, String>comparing(u -> {
      VirtualFile file = u.getVirtualFile();
      return file == null ? null : file.getName();
    }).thenComparingInt(u->{
      Segment range = u.getNavigationRange();
      return range == null ? 0 : range.getStartOffset();
    }));
    return results;
  }

  static void doMigration(PsiElement elementToBind, String newQName, UsageInfo[] usages, ArrayList<? super SmartPsiElementPointer<PsiElement>> refsToShorten) {
    try {
      SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(elementToBind.getProject());
      // rename all references
      for (UsageInfo usage : usages) {
        if (usage instanceof MigrationProcessor.MigrationUsageInfo) {
          final MigrationProcessor.MigrationUsageInfo usageInfo = (MigrationProcessor.MigrationUsageInfo)usage;
          if (Objects.equals(newQName, usageInfo.mapEntry.getNewName())) {
            PsiElement element = usage.getElement();
            if (element == null || !element.isValid()) continue;
            PsiElement psiElement;
            if (element instanceof PsiJavaCodeReferenceElement) {
              psiElement = ((PsiJavaCodeReferenceElement)element).bindToElement(elementToBind);
            }
            else {
              psiElement = bindNonJavaReference(elementToBind, element, usage);
            }
            if (psiElement != null) {
              refsToShorten.add(smartPointerManager.createSmartPsiElementPointer(psiElement));
            }
          }
        }
      }
    }
    catch (IncorrectOperationException e) {
      // should not happen!
      LOG.error(e);
    }
  }

  static PsiPackage findOrCreatePackage(Project project, final PsiMigration migration, final String qName) {
    PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(qName);
    if (aPackage != null) {
      return aPackage;
    }
    else {
      return WriteAction.compute(() -> migration.createPackage(qName));
    }
  }

  static PsiClass[] findOrCreateClass(Project project, final PsiMigration migration, final String qName) {
    PsiClass[] classes = JavaPsiFacade.getInstance(project).findClasses(qName, GlobalSearchScope.allScope(project));
    if (classes.length == 0) {
      classes = WriteAction.compute(() -> new PsiClass[] {migration.createClass(qName)});
    }
    return classes;
  }
}
