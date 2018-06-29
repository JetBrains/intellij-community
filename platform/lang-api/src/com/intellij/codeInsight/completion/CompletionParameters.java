// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public final class CompletionParameters {
  private final PsiElement myPosition;
  private final PsiFile myOriginalFile;
  private final CompletionType myCompletionType;
  @NotNull private final Editor myEditor;
  private final int myOffset;
  private final int myInvocationCount;
  private final CompletionProcess myProcess;

  CompletionParameters(@NotNull final PsiElement position, @NotNull final PsiFile originalFile,
                       @NotNull CompletionType completionType, int offset, int invocationCount, @NotNull Editor editor,
                       @NotNull CompletionProcess process) {
    PsiUtilCore.ensureValid(position);
    assert position.getTextRange().containsOffset(offset) : position;
    myPosition = position;
    myOriginalFile = originalFile;
    myCompletionType = completionType;
    myOffset = offset;
    myInvocationCount = invocationCount;
    myEditor = editor;
    myProcess = process;
  }

  @NotNull
  public CompletionParameters delegateToClassName() {
    return withType(CompletionType.CLASS_NAME).withInvocationCount(myInvocationCount - 1);
  }

  @NotNull
  public CompletionParameters withType(@NotNull CompletionType type) {
    return new CompletionParameters(myPosition, myOriginalFile, type, myOffset, myInvocationCount, myEditor, myProcess);
  }

  @NotNull
  public CompletionParameters withInvocationCount(int newCount) {
    return new CompletionParameters(myPosition, myOriginalFile, myCompletionType, myOffset, newCount, myEditor, myProcess);
  }

  @NotNull
  public PsiElement getPosition() {
    return myPosition;
  }

  @Nullable
  public PsiElement getOriginalPosition() {
    return myOriginalFile.findElementAt(myPosition.getTextRange().getStartOffset());
  }

  @NotNull
  public PsiFile getOriginalFile() {
    return myOriginalFile;
  }

  @NotNull
  public CompletionType getCompletionType() {
    return myCompletionType;
  }

  public int getOffset() {
    return myOffset;
  }

  /**
   * @return
   * 0 for autopopup
   * 1 for explicitly invoked completion
   * >1 for next completion invocations when one lookup is already active
   */
  public int getInvocationCount() {
    return myInvocationCount;
  }

  public boolean isAutoPopup() {
    return myInvocationCount == 0;
  }

  @NotNull
  public CompletionParameters withPosition(@NotNull PsiElement element, int offset) {
    return new CompletionParameters(element, myOriginalFile, myCompletionType, offset, myInvocationCount, myEditor, myProcess);
  }

  public boolean isExtendedCompletion() {
    return myCompletionType == CompletionType.BASIC && myInvocationCount >= 2;
  }

  /**
   * @return the editor where the completion was started
   */
  @NotNull
  public Editor getEditor() {
    return myEditor;
  }

  @NotNull
  public CompletionProcess getProcess() {
    return myProcess;
  }
}
