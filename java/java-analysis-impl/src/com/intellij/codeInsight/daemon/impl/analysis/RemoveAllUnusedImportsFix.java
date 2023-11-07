// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class RemoveAllUnusedImportsFix implements IntentionAction {
  @Override
  public @NotNull String getFamilyName() {
    return JavaErrorBundle.message("remove.unused.imports.quickfix.text");
  }

  @Override
  public @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return file instanceof PsiJavaFile && editor != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    if (!(psiFile instanceof PsiJavaFile javaFile) || editor == null) return;
    PsiImportList importList = javaFile.getImportList();
    if (importList == null) return;
    List<PsiImportStatement> importStatements = new ArrayList<>();
    DaemonCodeAnalyzerEx.processHighlights(editor.getDocument(), project, HighlightSeverity.INFORMATION, importList.getTextRange().getStartOffset(), importList.getTextRange().getEndOffset(), info -> {
      if (PostHighlightingVisitor.isUnusedImportHighlightInfo(psiFile, info)) {
        PsiImportStatement importStatement = PsiTreeUtil.findElementOfClassAtOffset(psiFile, info.getActualStartOffset(), PsiImportStatement.class, false);
        if (importStatement != null) {
          importStatements.add(importStatement);
        }
      }
      return true;
    });

    if (!importStatements.isEmpty()) {
      IntentionAction deleteAll = QuickFixFactory.getInstance().createDeleteFix(importStatements.toArray(PsiElement.EMPTY_ARRAY));
      deleteAll.invoke(project, editor, psiFile);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
