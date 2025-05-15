// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Quick fix that creates a new directory in one of the target directories. Automatically creates all intermediate directories of
 * {@link TargetDirectory#getPathToCreate()} and {@link NewFileLocation#getSubPath()}. If there are multiple target directories, it shows
 * a popup where users can select the desired target directory.
 */
public final class CreateDirectoryPathFix extends AbstractCreateFileFix {
  // invoked from another module
  @SuppressWarnings("WeakerAccess")
  public CreateDirectoryPathFix(@NotNull PsiElement psiElement,
                                @NotNull NewFileLocation newFileLocation,
                                @NotNull String fixLocaleKey) {
    super(psiElement, newFileLocation, fixLocaleKey);

    myIsAvailable = true;
    myIsAvailableTimeStamp = System.currentTimeMillis();
  }

  public CreateDirectoryPathFix(@NotNull PsiElement psiElement,
                                @NotNull NewFileLocation newFileLocation) {
    this(psiElement, newFileLocation, "create.directory.text");
  }

  @Override
  public @NotNull String getText() {
    return CodeInsightBundle.message(myKey, myNewFileName);
  }

  @Override
  public @NotNull String getFamilyName() {
    return CodeInsightBundle.message("create.directory.family");
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return null;
  }

  @Override
  protected void apply(@NotNull Project project, @NotNull Supplier<? extends @Nullable PsiDirectory> targetDirectory, @Nullable Editor editor)
    throws IncorrectOperationException {

    try {
      WriteCommandAction.writeCommandAction(project)
        .withName(CodeInsightBundle.message(myKey, myNewFileName))
        .run(() -> {
          var parent = targetDirectory.get();
          if (parent != null) {
            parent.createSubdirectory(myNewFileName);
          }
        });
    }
    catch (IncorrectCreateFilePathException e) {
      if (editor != null) {
        HintManager.getInstance().showErrorHint(editor, e.getLocalizedMessage());
      }
    }
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    HtmlChunk description = getDescription(AllIcons.Nodes.Folder);
    return description == null ? IntentionPreviewInfo.EMPTY : new IntentionPreviewInfo.Html(description);
  }
}
