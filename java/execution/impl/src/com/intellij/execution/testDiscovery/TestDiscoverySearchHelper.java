/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.testDiscovery;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.actions.FormatChangedTextUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

public class TestDiscoverySearchHelper {
  public static Set<String> search(final Project project, 
                                   final Pair<String, String> position, 
                                   final String changeList,
                                   final String frameworkPrefix) {
    final Set<String> patterns = new LinkedHashSet<String>();
    if (position != null) {
      try {
        collectPatterns(project, patterns, position.first, position.second, frameworkPrefix);
      }
      catch (IOException ignore) {
      }
    }
    final List<VirtualFile> files = getAffectedFiles(changeList, project);
    final PsiManager psiManager = PsiManager.getInstance(project);
    final TestDiscoveryIndex discoveryIndex = TestDiscoveryIndex.getInstance(project);
    for (final VirtualFile file : files) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          final PsiFile psiFile = psiManager.findFile(file);
          if (psiFile instanceof PsiClassOwner) {
            if (position != null) {
              final PsiClass[] classes = ((PsiClassOwner)psiFile).getClasses();
              if (classes.length == 0 || TestFrameworks.detectFramework(classes[0]) == null) return;
            }
            try {
              final List<TextRange> changedTextRanges = FormatChangedTextUtil.getInstance().getChangedTextRanges(project, psiFile);
              for (TextRange textRange : changedTextRanges) {
                final PsiElement start = psiFile.findElementAt(textRange.getStartOffset());
                final PsiElement end = psiFile.findElementAt(textRange.getEndOffset());
                final PsiElement parent = PsiTreeUtil.findCommonParent(new PsiElement[]{start, end});
                final Collection<PsiMethod> methods = new ArrayList<PsiMethod>(PsiTreeUtil.findChildrenOfType(parent, PsiMethod.class));
                final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(parent, PsiMethod.class);
                if (containingMethod != null) {
                  methods.add(containingMethod);
                }
                for (PsiMethod changedMethod : methods) {
                  final LinkedHashSet<String> detectedPatterns = position == null ? collectPatterns(changedMethod, frameworkPrefix) : null;
                  if (detectedPatterns != null) {
                    patterns.addAll(detectedPatterns);
                  }
                  final PsiClass containingClass = changedMethod.getContainingClass();
                  if (containingClass != null && containingClass.getParent() == psiFile) {
                    final String classQualifiedName = containingClass.getQualifiedName();
                    final String changedMethodName = changedMethod.getName();
                    try {
                      if (classQualifiedName != null &&
                          (position == null && TestFrameworks.detectFramework(containingClass) != null || 
                           position != null && !discoveryIndex.hasTestTrace(frameworkPrefix + classQualifiedName + "-" + changedMethodName))) {
                        patterns.add(classQualifiedName + "," + changedMethodName);
                      }
                    }
                    catch (IOException ignore) {}
                  }
                }
              }
            }
            catch (FilesTooBigForDiffException ignore) {
            }
          }
        }
      });
    }

    return patterns;
  }

  private static void collectPatterns(final Project project,
                                      final Set<String> patterns,
                                      final String classFQName,
                                      final String methodName,
                                      final String frameworkId) throws IOException {
    final TestDiscoveryIndex discoveryIndex = TestDiscoveryIndex.getInstance(project);
    final Collection<String> testsByMethodName = discoveryIndex.getTestsByMethodName(classFQName, methodName);
    if (testsByMethodName != null) {
      for (String pattern : ContainerUtil.filter(testsByMethodName, new Condition<String>() {
        @Override
        public boolean value(String s) {
          return s.startsWith(frameworkId);
        }
      })) {
        patterns.add(pattern.substring(frameworkId.length()).replace('-', ','));
      }
    }
  }

  @NotNull
  private static List<VirtualFile> getAffectedFiles(String changeListName, Project project) {
    final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    if (changeListName == null) {
      return changeListManager.getAffectedFiles();
    }
    final LocalChangeList changeList = changeListManager.findChangeList(changeListName);
    if (changeList != null) {
      List<VirtualFile> files = new ArrayList<VirtualFile>();
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

  @Nullable
  private static LinkedHashSet<String> collectPatterns(PsiMethod psiMethod, String frameworkId) {
    LinkedHashSet<String> patterns = new LinkedHashSet<String>();
    final PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass != null) {
      final String qualifiedName = containingClass.getQualifiedName();
      if (qualifiedName != null) {
        try {
          collectPatterns(psiMethod.getProject(), patterns, qualifiedName, psiMethod.getName(), frameworkId);
        }
        catch (IOException e) {
          return null;
        }
      }
    }
    return patterns;
  }
}
