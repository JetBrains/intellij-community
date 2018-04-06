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
  @Nullable private final Editor myEditor;
  private final int myOffset;
  private final int myInvocationCount;
  private final CompletionProcess myProcess;

  CompletionParameters(@NotNull final PsiElement position, @NotNull final PsiFile originalFile,
                       @NotNull CompletionType completionType, int offset, int invocationCount, @Nullable Editor editor,
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
   * @return the editor where the completion was started, or null if completion API was invoked in absence of editor.
   * @see #hasEditor()
   */
  @NotNull
  public Editor getEditor() {
    if (myEditor == null) throw new CompletionWithoutEditorException(); 
    return myEditor;
  }

  /** 
   * @return whether this completion was started within an editor
   * @since 181.*
   */
  public boolean hasEditor() {
    return myEditor != null;
  }

  @NotNull
  public CompletionProcess getProcess() {
    return myProcess;
  }
}
