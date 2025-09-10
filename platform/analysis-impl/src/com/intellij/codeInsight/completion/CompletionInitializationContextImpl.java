// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Internal
public class CompletionInitializationContextImpl extends CompletionInitializationContext {
  private final OffsetsInFile myHostOffsets;

  public CompletionInitializationContextImpl(@NotNull Editor editor,
                                             @NotNull Caret caret,
                                             @NotNull PsiFile file,
                                             @NotNull CompletionType completionType,
                                             int invocationCount) {
    super(editor, caret, PsiUtilBase.getLanguageInEditor(editor, file.getProject()), file, completionType, invocationCount);
    myHostOffsets = new OffsetsInFile(file, getOffsetMap()).toTopLevelFile();
  }

  public @NotNull OffsetsInFile getHostOffsets() {
    return myHostOffsets;
  }
}
