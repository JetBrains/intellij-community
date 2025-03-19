// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.SequentialTask;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

public final class OptimizeImportsRefactoringHelper implements RefactoringHelper<Set<PsiJavaFile>> {
  private static Set<PsiJavaFile> prepareOperation(final UsageInfo @NotNull [] usages) {
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
  public @Unmodifiable Set<PsiJavaFile> prepareOperation(UsageInfo @NotNull [] usages, @NotNull List<? extends @NotNull PsiElement> elements) {
    Set<PsiJavaFile> movedFiles = ContainerUtil.map2SetNotNull(elements, e -> ObjectUtils.tryCast(e.getContainingFile(), PsiJavaFile.class));
    return ContainerUtil.union(movedFiles, prepareOperation(usages));
  }

  @Override
  public void performOperation(@NotNull Project project, Set<PsiJavaFile> javaFiles) {
    if (javaFiles.isEmpty()) return;

    CodeStyleManager.getInstance(project).performActionWithFormatterDisabled(
      (Runnable)() -> PsiDocumentManager.getInstance(project).commitAllDocuments());

    ProgressManager progressManager = ProgressManager.getInstance();
    DumbService.getInstance(project).completeJustSubmittedTasks();
    final List<SmartPsiElementPointer<PsiImportStatementBase>> redundants = new ArrayList<>();
    final Runnable findRedundantImports = () -> {
      final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
      final ProgressIndicator progressIndicator = progressManager.getProgressIndicator();
      if (progressIndicator != null) {
        progressIndicator.setIndeterminate(false);
      }
      final SmartPointerManager pointerManager = SmartPointerManager.getInstance(project);
      int i = 0;
      final int fileCount = javaFiles.size();
      for (PsiJavaFile file : javaFiles) {
        double fraction = (double)i++ / fileCount;
        ReadAction.run(() -> {
          if (!file.isValid()) return;
          final VirtualFile virtualFile = file.getVirtualFile();
          if (virtualFile == null) return;
          if (progressIndicator != null) {
            progressIndicator.setText2(virtualFile.getPresentableUrl());
            progressIndicator.setFraction(fraction);
          }
          final Collection<PsiImportStatementBase> perFile = styleManager.findRedundantImports(file);
          if (perFile == null) return;
          for (PsiImportStatementBase redundant : perFile) {
            redundants.add(pointerManager.createSmartPsiElementPointer(redundant));
          }
        });
      }
    };

    String title = JavaRefactoringBundle.message("removing.redundant.imports.progress.title");
    if (!progressManager.runProcessWithProgressSynchronously(findRedundantImports, title, false, project)) return;
    if (redundants.isEmpty()) return;

    ApplicationManager.getApplication().runWriteAction(() -> {
      final SequentialModalProgressTask progressTask = new SequentialModalProgressTask(project, title, false);
      progressTask.setMinIterationTime(200);
      progressTask.setTask(new OptimizeImportsTask(progressTask, redundants));
      progressManager.run(progressTask);
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

  OptimizeImportsTask(SequentialModalProgressTask progressTask, Collection<SmartPsiElementPointer<PsiImportStatementBase>> pointers) {
    myTask = progressTask;
    myTotal = pointers.size();
    myPointers = pointers.iterator();
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
}