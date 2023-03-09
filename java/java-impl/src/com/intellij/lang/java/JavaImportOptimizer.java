// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java;

import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.java.JavaBundle;
import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.templateLanguages.TemplateLanguageUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class JavaImportOptimizer implements ImportOptimizer {
  private static final Logger LOG = Logger.getInstance(JavaImportOptimizer.class);

  @Override
  @NotNull
  public Runnable processFile(@NotNull PsiFile file) {
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
        try {
          final PsiDocumentManager manager = PsiDocumentManager.getInstance(file.getProject());
          final Document document = manager.getDocument(file);
          if (document != null) {
            manager.commitDocument(document);
          }
          final PsiImportList oldImportList = ((PsiJavaFile)file).getImportList();
          assert oldImportList != null;
          final List<String> oldImports = new ArrayList<>();
          for (PsiImportStatementBase statement : oldImportList.getAllImportStatements()) {
            oldImports.add(statement.getText());
          }
          oldImportList.replace(newImportList);
          for (PsiImportStatementBase statement : newImportList.getAllImportStatements()) {
            if (!oldImports.remove(statement.getText())) {
              myImportsAdded++;
            }
          }
          myImportsRemoved += oldImports.size();
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
    if (file instanceof PsiJavaFile && !TemplateLanguageUtil.isTemplateDataFile(file)) {
      VirtualFile virtualFile = PsiUtilCore.getVirtualFile(file);
      return virtualFile != null && (ProjectRootManager.getInstance(file.getProject()).getFileIndex().isInSource(virtualFile) ||
                                     ScratchUtil.isScratch(virtualFile));
    }
    return false;
  }
}
