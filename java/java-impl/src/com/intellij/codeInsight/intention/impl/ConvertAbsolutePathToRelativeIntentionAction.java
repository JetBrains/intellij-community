/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiWhiteSpace;
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
  @NotNull
  public String getFamilyName() {
    return "Convert " + (isConvertToRelative() ? "absolute" : "relative") + " path to " + (isConvertToRelative() ? "relative" : "absolute");
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;

    final PsiReference reference = file.findReferenceAt(editor.getCaretModel().getOffset());
    final FileReference fileReference = reference == null ? null : FileReference.findFileReference(reference);
    if (fileReference != null) {
      final FileReference lastReference = fileReference.getFileReferenceSet().getLastReference();
      if (lastReference != null) lastReference.bindToElement(lastReference.resolve(), !isConvertToRelative());
    }
  }

  @NotNull
  @Override
  public String getText() {
    return "Convert path to " + (isConvertToRelative() ? "relative" : "absolute");
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
