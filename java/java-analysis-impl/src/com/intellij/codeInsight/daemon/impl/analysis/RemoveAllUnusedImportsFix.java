// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RemoveAllUnusedImportsFix implements ModCommandAction {
  private final List<SmartPsiElementPointer<PsiImportStatementBase>> myUnusedImportPointerList;


  public RemoveAllUnusedImportsFix(@NotNull List<PsiImportStatementBase> unusedImportList) {
    myUnusedImportPointerList = ContainerUtil.map(unusedImportList, SmartPointerManager::createPointer);
  }

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
    if (!(context.file() instanceof PsiJavaFile)) return ModCommand.nop();
    List<PsiImportStatementBase> importStatements = ContainerUtil.mapNotNull(myUnusedImportPointerList, SmartPsiElementPointer::getElement);

    return removeImports(context, importStatements);
  }

  protected static @NotNull ModCommand removeImports(@NotNull ActionContext context, List<PsiImportStatementBase> importStatements) {
    if (importStatements.isEmpty()) return ModCommand.nop();
    return ModCommand.psiUpdate(context, updater -> {
      for (PsiImportStatementBase statement : ContainerUtil.map(importStatements, updater::getWritable)) {
        new CommentTracker().deleteAndRestoreComments(statement);
      }
    });
  }
}
