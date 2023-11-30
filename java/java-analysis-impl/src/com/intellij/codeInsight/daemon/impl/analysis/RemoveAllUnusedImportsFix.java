// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RemoveAllUnusedImportsFix implements ModCommandAction {
  @Override
  public @NotNull String getFamilyName() {
    return JavaErrorBundle.message("remove.unused.imports.quickfix.text");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    return context.file() instanceof PsiJavaFile ? Presentation.of(getFamilyName()) : null;
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    if (!(context.file() instanceof PsiJavaFile javaFile)) return ModCommand.nop();
    PsiImportList importList = javaFile.getImportList();
    if (importList == null) return ModCommand.nop();
    List<PsiImportStatement> importStatements = new ArrayList<>();
    DaemonCodeAnalyzerEx.processHighlights(javaFile.getViewProvider().getDocument(), context.project(), HighlightSeverity.INFORMATION, 
                                           importList.getTextRange().getStartOffset(), 
                                           importList.getTextRange().getEndOffset(), info -> {
      if (PostHighlightingVisitor.isUnusedImportHighlightInfo(javaFile, info)) {
        PsiImportStatement importStatement = PsiTreeUtil.findElementOfClassAtOffset(javaFile, info.getActualStartOffset(), PsiImportStatement.class, false);
        if (importStatement != null) {
          importStatements.add(importStatement);
        }
      }
      return true;
    });

    if (importStatements.isEmpty()) return ModCommand.nop();
    return ModCommand.psiUpdate(context, updater -> {
      for (PsiImportStatement statement : ContainerUtil.map(importStatements, updater::getWritable)) {
        new CommentTracker().deleteAndRestoreComments(statement);
      }
    });
  }
}
