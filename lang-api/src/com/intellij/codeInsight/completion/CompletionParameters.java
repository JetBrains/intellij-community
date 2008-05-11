/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * @author peter
 */
public class CompletionParameters {
  private final PsiElement myPosition;
  private final PsiFile myOriginalFile;
  private final CompletionType myCompletionType;
  private final int myOffset;
  private final int myInvocationCount;

  public CompletionParameters(@NotNull final PsiElement position, @NotNull final PsiFile originalFile,
                                  final CompletionType completionType, int offset, final int invocationCount) {
    assert offset >= position.getTextRange().getStartOffset();
    myPosition = position;
    myOriginalFile = originalFile;
    myCompletionType = completionType;
    myOffset = offset;
    myInvocationCount = invocationCount;
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

  public int getInvocationCount() {
    return myInvocationCount;
  }
}
