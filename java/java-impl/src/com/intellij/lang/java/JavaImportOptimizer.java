// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.java.JavaBundle;
import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.impl.source.jsp.jspJava.JspxImportList;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.templateLanguages.TemplateLanguageUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public final class JavaImportOptimizer implements ImportOptimizer {
  private static final Logger LOG = Logger.getInstance(JavaImportOptimizer.class);

  @Override
  public @NotNull Runnable processFile(@NotNull PsiFile file) {
    if (!(file instanceof PsiJavaFile javaFile) || !CodeStyle.isFormattingEnabled(file)) {
      return EmptyRunnable.getInstance();
    }
    Project project = file.getProject();
    final PsiImportList newImportList = JavaCodeStyleManager.getInstance(project).prepareOptimizeImportsResult(javaFile);
    if (newImportList == null) return EmptyRunnable.getInstance();

    return new CollectingInfoRunnable() {
      private int myImportsAdded;
      private int myImportsRemoved;

      @Override
      public void run() {
        try {
          PsiDocumentManager.getInstance(file.getProject()).commitDocument(file.getFileDocument());
          final PsiImportList oldImportList = javaFile.getImportList();
          assert oldImportList != null;
          if (oldImportList instanceof JspxImportList) {
            oldImportList.replace(newImportList);
          }
          else {
            oldImportList.getParent()
              .addRangeAfter(newImportList.getParent().getFirstChild(), newImportList.getParent().getLastChild(), oldImportList);
            oldImportList.delete();
          }
          myImportsAdded = ImportHelper.getImportsAdded(newImportList);
          myImportsRemoved = ImportHelper.getImportsRemoved(newImportList);
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
    if (file instanceof PsiJavaFile && !(file instanceof JspFile) && !TemplateLanguageUtil.isTemplateDataFile(file)) {
      VirtualFile virtualFile = PsiUtilCore.getVirtualFile(file);
      return virtualFile != null && (ProjectRootManager.getInstance(file.getProject()).getFileIndex().isInSource(virtualFile) ||
                                     virtualFile instanceof LightVirtualFile ||
                                     ScratchUtil.isScratch(virtualFile));
    }
    return false;
  }
}
