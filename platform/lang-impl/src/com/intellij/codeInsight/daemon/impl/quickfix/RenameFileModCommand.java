// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.modcommand.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.completion.command.CommandCompletionServiceKt.COMMAND_COMPLETION_COPY;

/**
 * A command to rename file; available only if target file does not exist
 */
public class RenameFileModCommand implements ModCommandAction {
  private final String myNewFileName;

  /**
   * @param newFileName with extension
   */
  public RenameFileModCommand(@NotNull String newFileName) {
    myNewFileName = newFileName;
  }

  @Override
  public @NotNull String getFamilyName() {
    return CodeInsightBundle.message("rename.file.fix");
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    PsiFile psiFile = context.file();
    if (psiFile.getCopyableUserData(COMMAND_COMPLETION_COPY) == Boolean.TRUE) {
      psiFile = psiFile.getOriginalFile();
    }
    VirtualFile virtualFile = psiFile.getVirtualFile();
    return new ModMoveFile(virtualFile, new FutureVirtualFile(virtualFile.getParent(), myNewFileName, virtualFile.getFileType()));
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    PsiFile psiFile = context.file();
    VirtualFile vFile = psiFile.getVirtualFile();
    if (vFile == null) return null;
    VirtualFile parent = vFile.getParent();
    if (parent == null && psiFile.getCopyableUserData(COMMAND_COMPLETION_COPY) == Boolean.TRUE) {
      psiFile = psiFile.getOriginalFile();
      vFile = psiFile.getVirtualFile();
      if (vFile == null) return null;
      parent = vFile.getParent();
    }
    if (parent == null) return null;
    VirtualFile newVFile = parent.findChild(myNewFileName);
    return newVFile == null || newVFile.equals(vFile) ? Presentation.of(getFamilyName()) : null;
  }
}