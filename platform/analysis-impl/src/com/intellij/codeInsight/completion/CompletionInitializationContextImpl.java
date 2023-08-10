// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  CompletionInitializationContextImpl(Editor editor,
                                      @NotNull Caret caret,
                                      PsiFile file,
                                      CompletionType completionType, int invocationCount) {
    super(editor, caret, PsiUtilBase.getLanguageInEditor(editor, file.getProject()), file, completionType, invocationCount);
    myHostOffsets = new OffsetsInFile(file, getOffsetMap()).toTopLevelFile();
  }

  @NotNull
  public OffsetsInFile getHostOffsets() {
    return myHostOffsets;
  }
}
