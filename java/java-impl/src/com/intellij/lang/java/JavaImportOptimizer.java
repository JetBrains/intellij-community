// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang.java;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.intellij.java.JavaBundle;
import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.templateLanguages.TemplateLanguageUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.NotNull;

public class JavaImportOptimizer implements ImportOptimizer {
  private static final Logger LOG = Logger.getInstance(JavaImportOptimizer.class);

  @Override
  @NotNull
  public Runnable processFile(@NotNull final PsiFile file) {
    if (!(file instanceof PsiJavaFile)) {
      return EmptyRunnable.getInstance();
    }
    Project project = file.getProject();
    final PsiImportList newImportList = JavaCodeStyleManager.getInstance(project).prepareOptimizeImportsResult((PsiJavaFile)file);
    if (newImportList == null) return EmptyRunnable.getInstance();

    return new CollectingInfoRunnable() {
      private int myImportsAdded;
      private int myImportsRemoved;

      @Override
      public void run() {
        SlowOperations.allowSlowOperations(() -> doRun());
      }

      private void doRun() {
        try {
          final PsiDocumentManager manager = PsiDocumentManager.getInstance(file.getProject());
          final Document document = manager.getDocument(file);
          if (document != null) {
            manager.commitDocument(document);
          }
          final PsiImportList oldImportList = ((PsiJavaFile)file).getImportList();
          assert oldImportList != null;
          final Multiset<PsiElement> oldImports = HashMultiset.create();
          for (PsiImportStatement statement : oldImportList.getImportStatements()) {
            oldImports.add(statement.resolve());
          }

          final Multiset<PsiElement> oldStaticImports = HashMultiset.create();
          for (PsiImportStaticStatement statement : oldImportList.getImportStaticStatements()) {
            oldStaticImports.add(statement.resolve());
          }

          oldImportList.replace(newImportList);
          for (PsiImportStatement statement : newImportList.getImportStatements()) {
            if (!oldImports.remove(statement.resolve())) {
              myImportsAdded++;
            }
          }
          myImportsRemoved += oldImports.size();

          for (PsiImportStaticStatement statement : newImportList.getImportStaticStatements()) {
            if (!oldStaticImports.remove(statement.resolve())) {
              myImportsAdded++;
            }
          }
          myImportsRemoved += oldStaticImports.size();
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      @Override
      public String getUserNotificationInfo() {
        if (myImportsRemoved == 0) {
          return JavaBundle.message("hint.text.rearranged.imports");
        }
        String notification = JavaBundle.message("hint.text.removed.imports", myImportsRemoved, myImportsRemoved == 1 ? 0 : 1);
        if (myImportsAdded > 0) {
          notification += JavaBundle.message("hint.text.added.imports", myImportsAdded, myImportsAdded == 1 ? 0 : 1);
        }
        return notification;
      }
    };
  }

  @Override
  public boolean supports(@NotNull PsiFile file) {
    return file instanceof PsiJavaFile
           && !TemplateLanguageUtil.isTemplateDataFile(file)
           && ProjectRootManager.getInstance(file.getProject()).getFileIndex().isInSource(file.getViewProvider().getVirtualFile())
      ;
  }
}
