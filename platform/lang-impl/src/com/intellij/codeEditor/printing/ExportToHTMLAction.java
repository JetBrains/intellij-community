// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeEditor.printing;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.nio.file.NoSuchFileException;

@ApiStatus.Internal
public final class ExportToHTMLAction extends AnAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    try {
      new ExportToHTMLManager().executeExport(dataContext);
    }
    catch (NoSuchFileException ex) {
      JOptionPane.showMessageDialog(null, EditorBundle.message("file.not.found", ex.getMessage()),
                                    CommonBundle.getErrorTitle(), JOptionPane.ERROR_MESSAGE);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();

    PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);
    if (psiFile != null) {
      presentation.setEnabled(psiFile.getContainingDirectory() != null);
      presentation.setVisible(true);
    }
    else {
      // psiFile is null => for the major of case it is NOT an editor context => no parsing => no freeze
      PsiElement psiElement = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
      if (psiElement instanceof PsiDirectory) {
        presentation.setEnabledAndVisible(true);
      }
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}