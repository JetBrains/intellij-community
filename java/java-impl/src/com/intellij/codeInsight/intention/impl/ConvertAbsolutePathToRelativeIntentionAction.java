/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.*;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.*;
import com.intellij.util.*;
import org.jetbrains.annotations.*;

/**
 * @author spleaner
 */
public class ConvertAbsolutePathToRelativeIntentionAction extends PsiElementBaseIntentionAction {

  protected boolean isConvertToRelative() {
    return true;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) return false;

    final PsiReference reference = containingFile.findReferenceAt(editor.getCaretModel().getOffset());
    final FileReference fileReference = reference == null ? null : findFileReference(reference);

    if (fileReference != null) {
      final FileReferenceSet set = fileReference.getFileReferenceSet();
      final FileReference lastReference = set.getLastReference();
      return set.couldBeConvertedTo(isConvertToRelative()) && lastReference != null &&
             (!isConvertToRelative() && !set.isAbsolutePathReference() || isConvertToRelative() && set.isAbsolutePathReference()) &&
             lastReference.resolve() != null;
    }

    return false;
  }

  @Nullable
  private static FileReference findFileReference(@NotNull final PsiReference original) {
    if (original instanceof PsiMultiReference) {
      final PsiMultiReference multiReference = (PsiMultiReference)original;
      for (PsiReference reference : multiReference.getReferences()) {
        if (reference instanceof FileReference) {
          return (FileReference)reference;
        }
      }
    }
    else if (original instanceof FileReferenceOwner) {
      final FileReference fileReference = ((FileReferenceOwner)original).getLastFileReference();
      if (fileReference != null) {
        return fileReference;
      }
    }

    return null;
  }

  @NotNull
  public String getFamilyName() {
    return "Convert " + (isConvertToRelative() ? "absolute" : "relative") + " path to " + (isConvertToRelative() ? "relative" : "absolute");
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiReference reference = file.findReferenceAt(editor.getCaretModel().getOffset());
    final FileReference fileReference = reference == null ? null : findFileReference(reference);
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
