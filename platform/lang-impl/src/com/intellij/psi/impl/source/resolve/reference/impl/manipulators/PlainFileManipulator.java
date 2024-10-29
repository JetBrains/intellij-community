// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class PlainFileManipulator extends AbstractElementManipulator<PsiPlainTextFile> {
  @Override
  public PsiPlainTextFile handleContentChange(@NotNull PsiPlainTextFile file, @NotNull TextRange range, String newContent)
  throws IncorrectOperationException {
    final Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
    document.replaceString(range.getStartOffset(), range.getEndOffset(), newContent);
    PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);

    return file;
  }
}
