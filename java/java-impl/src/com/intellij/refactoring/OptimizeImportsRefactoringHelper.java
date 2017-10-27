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
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.SequentialTask;
import com.intellij.util.containers.HashSet;

import java.util.*;

public class OptimizeImportsRefactoringHelper implements RefactoringHelper<Set<PsiJavaFile>> {
  private static final String REMOVING_REDUNDANT_IMPORTS_TITLE = "Removing redundant imports";

  @Override
  public Set<PsiJavaFile> prepareOperation(final UsageInfo[] usages) {
    Set<PsiJavaFile> javaFiles = new HashSet<>();
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
    CodeStyleManager.getInstance(project).performActionWithFormatterDisabled(
      (Runnable)() -> PsiDocumentManager.getInstance(project).commitAllDocuments());

    final List<SmartPsiElementPointer<PsiImportStatementBase>> redundants = new ArrayList<>();
    final Runnable findRedundantImports = () -> ReadAction.run(() -> {
      final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
      final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
      if (progressIndicator != null) {
        progressIndicator.setIndeterminate(false);
      }
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
    });

    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(findRedundantImports, REMOVING_REDUNDANT_IMPORTS_TITLE, false, project)) return;

    ApplicationManager.getApplication().runWriteAction(() -> {
      final SequentialModalProgressTask progressTask = new SequentialModalProgressTask(project, REMOVING_REDUNDANT_IMPORTS_TITLE, false);
      progressTask.setMinIterationTime(200);
      progressTask.setTask(new OptimizeImportsTask(progressTask, redundants));
      ProgressManager.getInstance().run(progressTask);
    });
  }
}


class OptimizeImportsTask implements SequentialTask {
  private static final Logger LOG = Logger.getInstance(OptimizeImportsTask.class);

  private final Iterator<SmartPsiElementPointer<PsiImportStatementBase>> myPointers;
  private final SequentialModalProgressTask myTask;
  private final int myTotal;
  private int myCount;
  private final Map<PsiFile, Set<String>> myDuplicates = new HashMap<>();

  public OptimizeImportsTask(SequentialModalProgressTask progressTask, Collection<SmartPsiElementPointer<PsiImportStatementBase>> pointers) {
    myTask = progressTask;
    myTotal = pointers.size();
    myPointers = pointers.iterator();
  }

  @Override
  public void prepare() {
  }

  @Override
  public boolean isDone() {
    return !myPointers.hasNext();
  }

  @Override
  public boolean iteration() {
    final ProgressIndicator indicator = myTask.getIndicator();
    if (indicator != null) {
      indicator.setFraction(((double)myCount ++) / myTotal);
    }

    final SmartPsiElementPointer<PsiImportStatementBase> pointer = myPointers.next();

    final PsiImportStatementBase importStatement = pointer.getElement();
    if (importStatement != null && importStatement.isValid()) {
      final PsiJavaCodeReferenceElement ref = importStatement.getImportReference();
      //Do not remove non-resolving refs
      if (ref != null) {
        final PsiElement resolve = ref.resolve();
        try {
          if (resolve != null) {
            if (!(resolve instanceof PsiPackage) || ((PsiPackage)resolve).getDirectories(ref.getResolveScope()).length != 0) {
              importStatement.delete();
            }
          }
          //preserve comments and don't need to distinguish static/normal imports and on-demand variations
          else {
            Collection<String> imports = myDuplicates.computeIfAbsent(pointer.getContainingFile(), file -> new HashSet<>());
            if (!imports.add(importStatement.getText())) {
              importStatement.delete();
            }
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }

    return isDone();
  }

  @Override
  public void stop() {
  }
}