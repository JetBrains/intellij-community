// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.intentions.openInProject;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LightEditIntention;
import com.intellij.ide.actions.OpenFileAction;
import com.intellij.ide.lightEdit.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class LightEditOpenInProjectIntention implements IntentionAction, LightEditIntention {
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Override
  public @NotNull String getText() {
    return ApplicationBundle.message("light.edit.open.in.project.intention");
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             Editor editor,
                             PsiFile file) {
    return LightEdit.owns(project);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    VirtualFile currFile = file.getVirtualFile();
    LightEditorInfo editorInfo =
      ((LightEditorManagerImpl)LightEditService.getInstance().getEditorManager()).findOpen(currFile);
    if (editorInfo != null) {
      VirtualFile projectRoot = ProjectRootSearchUtil.findProjectRoot(currFile);
      if (projectRoot != null) {
        Project openedProject =
          PlatformProjectOpenProcessor.getInstance().openProjectAndFile(projectRoot, -1, -1, false);
        if (openedProject != null) {
          ((LightEditServiceImpl)LightEditService.getInstance()).closeEditor(editorInfo);
          OpenFileAction.openFile(file.getVirtualFile(), openedProject);
        }
      }
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
