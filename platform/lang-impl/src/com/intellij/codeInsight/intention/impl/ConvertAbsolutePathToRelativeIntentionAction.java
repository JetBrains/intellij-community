// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class ConvertAbsolutePathToRelativeIntentionAction extends BaseIntentionAction {

  protected boolean isConvertToRelative() {
    return true;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement element = file.findElementAt(offset);
    if (element == null ||
        element instanceof PsiWhiteSpace) {
      return false;
    }

    final PsiReference reference = file.findReferenceAt(offset);
    final FileReference fileReference = reference == null ? null : FileReference.findFileReference(reference);

    if (fileReference != null) {
      final FileReferenceSet set = fileReference.getFileReferenceSet();
      final FileReference lastReference = set.getLastReference();
      return set.couldBeConvertedTo(isConvertToRelative()) && lastReference != null &&
             (!isConvertToRelative() && !set.isAbsolutePathReference() || isConvertToRelative() && set.isAbsolutePathReference()) &&
             lastReference.resolve() != null;
    }

    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiReference reference = file.findReferenceAt(editor.getCaretModel().getOffset());
    final FileReference fileReference = reference == null ? null : FileReference.findFileReference(reference);
    if (fileReference != null) {
      final FileReference lastReference = fileReference.getFileReferenceSet().getLastReference();
      if (lastReference != null) {
        PsiFileSystemItem item = lastReference.resolve();
        if (item != null) {
          lastReference.bindToElement(item, !isConvertToRelative());
        }
      }
    }
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.family.convert.absolute.path.to.relative");
  }

  @NotNull
  @Override
  public String getText() {
    return CodeInsightBundle.message("intention.text.convert.path.to.relative");
  }
}
