// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Quick fix that creates a new directory in one of the target directories. Automatically creates all intermediate directories of
 * {@link TargetDirectory#getPathToCreate()} and {@link NewFileLocation#getSubPath()}. If there are multiple target directories it shows
 * a popup where users can select desired target directory.
 */
public class CreateDirectoryPathFix extends AbstractCreateFileFix {
  // invoked from other module
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
  @NotNull
  public String getText() {
    return CodeInsightBundle.message(myKey, myNewFileName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("create.directory.family");
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return null;
  }

  @Override
  protected void apply(@NotNull Project project, @NotNull PsiDirectory targetDirectory, @Nullable Editor editor)
    throws IncorrectOperationException {

    targetDirectory.createSubdirectory(myNewFileName);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    HtmlChunk description = getDescription(AllIcons.Nodes.Folder);
    return description == null ? IntentionPreviewInfo.EMPTY : new IntentionPreviewInfo.Html(description);
  }
}
