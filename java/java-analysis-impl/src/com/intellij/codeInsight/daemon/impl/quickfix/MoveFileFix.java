// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class MoveFileFix implements IntentionAction {
  private final VirtualFile myFile;
  private final VirtualFile myTarget;
  private final @IntentionName String myMessage;

  public MoveFileFix(@NotNull VirtualFile file, @NotNull VirtualFile target, @NotNull @Nls String message) {
    myFile = file;
    myTarget = target;
    myMessage = message;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return IntentionPreviewInfo.moveToDirectory(myFile, myTarget);
  }

  @Override
  public @NotNull String getText() {
    return myMessage;
  }

  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    int offset = -1;
    if (editor.getVirtualFile().equals(myFile)) {
      offset = editor.getCaretModel().getOffset();
    }
    if (myFile.isValid() && myTarget.isValid()) {
      try {
        myFile.move(this, myTarget);
      }
      catch (IOException e) {
        new Notification("FileSystemIssue", 
                         JavaAnalysisBundle.message("notification.content.cannot.move.file", myFile.getPath(), myTarget.getPath(), e.getMessage()),
                         NotificationType.ERROR)
          .notify(project);
      }
    }
    FileEditorManager manager = FileEditorManager.getInstance(project);
    manager.closeFile(myFile);
    if (offset >= 0) {
      manager.openTextEditor(new OpenFileDescriptor(project, myFile, offset), true);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}