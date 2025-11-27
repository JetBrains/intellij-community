// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * See {@link #withType(CompletionType)}, {@link #withInvocationCount(int)} and {@link #withPosition(PsiElement, int)} to
 * create modified copy of given current parameters.
 *
 * @see CompletionContributor
 */
public final class CompletionParameters implements BaseCompletionParameters {
  private final PsiElement myPosition;
  private final PsiFile myOriginalFile;
  private final CompletionType myCompletionType;
  private final @NotNull Editor myEditor;
  private final int myOffset;
  private final int myInvocationCount;
  private final CompletionProcess myProcess;
  private boolean myIsTestingMode;
  private boolean myCompleteOnlyNotImported;

  private CompletionParameters(@NotNull PsiElement position,
                               @NotNull PsiFile originalFile,
                               @NotNull CompletionType completionType,
                               int offset,
                               int invocationCount,
                               @NotNull Editor editor,
                               @NotNull CompletionProcess process,
                               boolean isTestingMode,
                               boolean completeOnlyNotImported) {
    PsiUtilCore.ensureValid(position);
    assert position.getTextRange().containsOffset(offset) : position;
    myPosition = position;
    myOriginalFile = originalFile;
    myCompletionType = completionType;
    myOffset = offset;
    myInvocationCount = invocationCount;
    myEditor = editor;
    myProcess = process;
    myIsTestingMode = isTestingMode;
    myCompleteOnlyNotImported = completeOnlyNotImported;
  }

  @ApiStatus.Internal
  public CompletionParameters(@NotNull PsiElement position,
                              @NotNull PsiFile originalFile,
                              @NotNull CompletionType completionType,
                              int offset,
                              int invocationCount,
                              @NotNull Editor editor,
                              @NotNull CompletionProcess process) {
    this(position, originalFile, completionType, offset, invocationCount, editor, process, false, false);
  }

  public @NotNull CompletionParameters withType(@NotNull CompletionType type) {
    return new CompletionParameters(myPosition, myOriginalFile, type, myOffset, myInvocationCount, myEditor, myProcess, myIsTestingMode, myCompleteOnlyNotImported);
  }

  public @NotNull CompletionParameters withInvocationCount(int newCount) {
    return new CompletionParameters(myPosition, myOriginalFile, myCompletionType, myOffset, newCount, myEditor, myProcess, myIsTestingMode, myCompleteOnlyNotImported);
  }

  @Override
  public @NotNull PsiElement getPosition() {
    return myPosition;
  }

  public @Nullable PsiElement getOriginalPosition() {
    return myOriginalFile.findElementAt(myPosition.getTextRange().getStartOffset());
  }
  
  @Override
  public @NotNull PsiFile getOriginalFile() {
    return myOriginalFile;
  }

  public @NotNull CompletionType getCompletionType() {
    return myCompletionType;
  }

  @Override
  public int getOffset() {
    return myOffset;
  }

  /**
   * @return 0 for autopopup<br>
   * 1 for explicitly invoked completion<br>
   * >1 for next completion invocations when one lookup is already active
   */
  public int getInvocationCount() {
    return myInvocationCount;
  }

  public boolean isAutoPopup() {
    return myInvocationCount == 0;
  }

  public @NotNull CompletionParameters withPosition(@NotNull PsiElement element, int offset) {
    return new CompletionParameters(element, myOriginalFile, myCompletionType, offset, myInvocationCount, myEditor, myProcess, myIsTestingMode, myCompleteOnlyNotImported);
  }

  public boolean isExtendedCompletion() {
    return myCompletionType == CompletionType.BASIC && myInvocationCount >= 2;
  }

  /**
   * @return the editor where the completion was started
   */
  public @NotNull Editor getEditor() {
    return myEditor;
  }

  public @NotNull CompletionProcess getProcess() {
    return myProcess;
  }

  public boolean isTestingMode() {
    return myIsTestingMode;
  }

  public void setTestingMode(boolean runTestingMode) {
    myIsTestingMode = runTestingMode;
  }

  public boolean isCompleteOnlyNotImported() {
    return myCompleteOnlyNotImported;
  }

  public void setCompleteOnlyNotImported(boolean onlyNonImported) {
    myCompleteOnlyNotImported = onlyNonImported;
  }
}
