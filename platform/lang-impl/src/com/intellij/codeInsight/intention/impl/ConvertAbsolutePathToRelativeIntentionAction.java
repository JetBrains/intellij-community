// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class ConvertAbsolutePathToRelativeIntentionAction extends BaseIntentionAction {

  protected boolean isConvertToRelative() {
    return true;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null ||
        element instanceof PsiWhiteSpace) {
      return false;
    }

    PsiReference reference = file.findReferenceAt(offset);
    FileReference fileReference = reference == null ? null : FileReference.findFileReference(reference);

    if (fileReference != null) {
      FileReferenceSet set = fileReference.getFileReferenceSet();
      FileReference lastReference = set.getLastReference();
      return set.couldBeConvertedTo(isConvertToRelative()) && lastReference != null &&
             (!isConvertToRelative() && !set.isAbsolutePathReference() || isConvertToRelative() && set.isAbsolutePathReference()) &&
             lastReference.resolve() != null;
    }

    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiReference reference = file.findReferenceAt(editor.getCaretModel().getOffset());
    FileReference fileReference = reference == null ? null : FileReference.findFileReference(reference);
    if (fileReference != null) {
      FileReference lastReference = fileReference.getFileReferenceSet().getLastReference();
      if (lastReference != null) {
        PsiFileSystemItem item = lastReference.resolve();
        if (item != null) {
          lastReference.bindToElement(item, !isConvertToRelative());
        }
      }
    }
  }

  @Override
  public @NotNull String getFamilyName() {
    return CodeInsightBundle.message("intention.family.convert.absolute.path.to.relative");
  }

  @Override
  public @NotNull String getText() {
    return CodeInsightBundle.message("intention.text.convert.path.to.relative");
  }
}
