// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.actions.FormatChangedTextUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TestDiscoverySearchHelper {
  public static Set<String> search(final Project project,
                                   final Pair<String, String> position,
                                   final String changeList,
                                   byte frameworkId) {
    final Set<String> patterns = new LinkedHashSet<>();
    if (position != null) {
      collectPatterns(project, patterns, position.first, position.second, frameworkId);
    }
    final List<VirtualFile> files = getAffectedFiles(changeList, project);
    final PsiManager psiManager = PsiManager.getInstance(project);
    final TestDiscoveryIndex discoveryIndex = TestDiscoveryIndex.getInstance(project);
    for (final VirtualFile file : files) {
      ApplicationManager.getApplication().runReadAction(() -> {
        final PsiFile psiFile = psiManager.findFile(file);
        if (psiFile instanceof PsiClassOwner) {
          if (position != null) {
            final PsiClass[] classes = ((PsiClassOwner)psiFile).getClasses();
            if (classes.length == 0 || TestFrameworks.detectFramework(classes[0]) == null) return;
          }
          final List<TextRange> changedTextRanges = FormatChangedTextUtil.getInstance().getChangedTextRanges(project, psiFile);
          for (TextRange textRange : changedTextRanges) {
            final PsiElement start = psiFile.findElementAt(textRange.getStartOffset());
            final PsiElement end = psiFile.findElementAt(textRange.getEndOffset());
            final PsiElement parent = PsiTreeUtil.findCommonParent(new PsiElement[]{start, end});
            final Collection<PsiMethod> methods = new ArrayList<>(PsiTreeUtil.findChildrenOfType(parent, PsiMethod.class));
            final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(parent, PsiMethod.class);
            if (containingMethod != null) {
              methods.add(containingMethod);
            }
            for (PsiMethod changedMethod : methods) {
              final LinkedHashSet<String> detectedPatterns = position == null ? collectPatterns(changedMethod, frameworkId) : null;
              if (detectedPatterns != null) {
                patterns.addAll(detectedPatterns);
              }
              final PsiClass containingClass = changedMethod.getContainingClass();
              if (containingClass != null && containingClass.getParent() == psiFile) {
                final String classQualifiedName = containingClass.getQualifiedName();
                final String changedMethodName = changedMethod.getName();
                if (classQualifiedName != null &&
                    (position == null && TestFrameworks.detectFramework(containingClass) != null ||
                     position != null && !discoveryIndex.hasTestTrace(classQualifiedName, changedMethodName, frameworkId))) {
                  patterns.add(classQualifiedName + "," + changedMethodName);
                }
              }
            }
          }
        }
      });
    }

    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);
    return new HashSet<>(ContainerUtil.filter(patterns, fqn -> ReadAction.compute(() -> psiFacade.findClass(StringUtil.getPackageName(fqn, ','), searchScope) != null)));
  }

  private static void collectPatterns(@NotNull Project project,
                                      @NotNull Set<String> patterns,
                                      @NotNull String classFQName,
                                      @NotNull String methodName,
                                      byte frameworkId) {
    TestDiscoveryProducer.consumeDiscoveredTests(project, classFQName, methodName, frameworkId, (c, m, p) -> {
      patterns.add(c + "," + m);
      return true;
    });
  }

  @NotNull
  private static List<VirtualFile> getAffectedFiles(String changeListName, Project project) {
    final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    if ("All".equals(changeListName)) {
      return changeListManager.getAffectedFiles();
    }
    final LocalChangeList changeList = changeListManager.findChangeList(changeListName);
    if (changeList != null) {
      List<VirtualFile> files = new ArrayList<>();
      for (Change change : changeList.getChanges()) {
        final ContentRevision afterRevision = change.getAfterRevision();
        if (afterRevision != null) {
          final VirtualFile file = afterRevision.getFile().getVirtualFile();
          if (file != null) {
            files.add(file);
          }
        }
      }
      return files;
    }

    return Collections.emptyList();
  }

  @NotNull
  private static LinkedHashSet<String> collectPatterns(PsiMethod psiMethod, byte frameworkId) {
    LinkedHashSet<String> patterns = new LinkedHashSet<>();
    final PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass != null) {
      final String qualifiedName = containingClass.getQualifiedName();
      if (qualifiedName != null) {
        collectPatterns(psiMethod.getProject(), patterns, qualifiedName, psiMethod.getName(), frameworkId);
      }
    }
    return patterns;
  }
}
