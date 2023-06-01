// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class RenameFileFix implements IntentionAction, LocalQuickFix {
  private final String myNewFileName;

  /**
   * @param newFileName with extension
   */
  public RenameFileFix(@NotNull String newFileName) {
    myNewFileName = newFileName;
  }

  @Override
  public @NotNull String getText() {
    return CodeInsightBundle.message("rename.file.fix");
  }

  @Override
  public @NotNull String getName() {
    return getText();
  }

  @Override
  public @NotNull String getFamilyName() {
    return CodeInsightBundle.message("rename.file.fix");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiFile file = descriptor.getPsiElement().getContainingFile();
    if (isAvailable(project, null, file)) {
      WriteCommandAction.writeCommandAction(project).run(() -> invoke(project, null, file));
    }
  }

  @Override
  public final boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (file == null || !file.isValid()) return false;
    VirtualFile vFile = file.getVirtualFile();
    if (vFile == null) return false;
    VirtualFile parent = vFile.getParent();
    if (parent == null) return false;
    VirtualFile newVFile = parent.findChild(myNewFileName);
    return newVFile == null || newVFile.equals(vFile);
  }


  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    VirtualFile vFile = file.getVirtualFile();
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    FileDocumentManager.getInstance().saveDocument(document);
    try {
      vFile.rename(file.getManager(), myNewFileName);
    }
    catch (IOException e) {
      MessagesEx.error(project, e.getMessage()).showLater();
    }
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    return IntentionPreviewInfo.rename(previewDescriptor.getPsiElement().getContainingFile(), myNewFileName);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return IntentionPreviewInfo.rename(file, myNewFileName);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}