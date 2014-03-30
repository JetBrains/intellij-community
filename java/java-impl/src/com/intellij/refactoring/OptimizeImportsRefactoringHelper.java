/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;

import java.util.Collection;
import java.util.Set;

public class OptimizeImportsRefactoringHelper implements RefactoringHelper<Set<PsiJavaFile>> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.OptimizeImportsRefactoringHelper");

  @Override
  public Set<PsiJavaFile> prepareOperation(final UsageInfo[] usages) {
    Set<PsiJavaFile> javaFiles = new HashSet<PsiJavaFile>();
    for (UsageInfo usage : usages) {
      if (usage.isNonCodeUsage) continue;
      final PsiFile file = usage.getFile();
      if (file instanceof PsiJavaFile) {
        javaFiles.add((PsiJavaFile)file);
      }
    }
    return javaFiles;
  }

  @Override
  public void performOperation(final Project project, final Set<PsiJavaFile> javaFiles) {
    CodeStyleManager.getInstance(project).performActionWithFormatterDisabled(new Runnable() {
      @Override
      public void run() {
        PsiDocumentManager.getInstance(project).commitAllDocuments();
      }
    });

    final Set<SmartPsiElementPointer<PsiImportStatementBase>> redundants = new HashSet<SmartPsiElementPointer<PsiImportStatementBase>>();
    final Runnable findRedundantImports = new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
            final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
            final SmartPointerManager pointerManager = SmartPointerManager.getInstance(project);
            int i = 0;
            final int fileCount = javaFiles.size();
            for (PsiJavaFile file : javaFiles) {
              if (file.isValid()) {
                final VirtualFile virtualFile = file.getVirtualFile();
                if (virtualFile != null) {
                  if (progressIndicator != null) {
                    progressIndicator.setText2(virtualFile.getPresentableUrl());
                    progressIndicator.setFraction((double)i++ / fileCount);
                  }
                  final Collection<PsiImportStatementBase> perFile = styleManager.findRedundantImports(file);
                  if (perFile != null) {
                    for (PsiImportStatementBase redundant : perFile) {
                      redundants.add(pointerManager.createSmartPsiElementPointer(redundant));
                    }
                  }
                }
              }
            }
          }
        });
      }
    };

    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(findRedundantImports, "Removing redundant imports", false, project)) return;

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          for (final SmartPsiElementPointer<PsiImportStatementBase> pointer : redundants) {
            final PsiImportStatementBase importStatement = pointer.getElement();
            if (importStatement != null && importStatement.isValid()) {
              final PsiJavaCodeReferenceElement ref = importStatement.getImportReference();
              //Do not remove non-resolving refs
              if (ref == null) {
                continue;
              }
              final PsiElement resolve = ref.resolve();
              if (resolve == null) {
                continue;
              }

              if (resolve instanceof PsiPackage && ((PsiPackage)resolve).getDirectories(ref.getResolveScope()).length == 0) {
                continue;
              }
              importStatement.delete();
            }
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
  }
}
