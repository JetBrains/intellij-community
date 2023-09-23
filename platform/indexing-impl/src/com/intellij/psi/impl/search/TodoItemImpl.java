// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.search;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.TodoItem;
import com.intellij.psi.search.TodoPattern;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class TodoItemImpl implements TodoItem {
  private final PsiFile myFile;
  private final int myStartOffset;
  private final int myEndOffset;
  private final TodoPattern myPattern;
  private final List<TextRange> myAdditionalRanges;

  public TodoItemImpl(@NotNull PsiFile file, int startOffset, int endOffset, @NotNull TodoPattern pattern,
                      @NotNull List<TextRange> additionalRanges) {
    myFile = file;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myPattern = pattern;
    myAdditionalRanges = additionalRanges;
  }

  @Override
  public @NotNull PsiFile getFile() {
    return myFile;
  }

  @Override
  public @NotNull TextRange getTextRange() {
    return new TextRange(myStartOffset, myEndOffset);
  }

  @Override
  public @NotNull List<TextRange> getAdditionalTextRanges() {
    return myAdditionalRanges;
  }

  @Override
  public @NotNull TodoPattern getPattern() {
    return myPattern;
  }

  public int hashCode(){
    return myFile.hashCode()+myStartOffset+myEndOffset+myPattern.hashCode();
  }

  public boolean equals(Object obj){
    if(!(obj instanceof TodoItemImpl todoItem)){
      return false;
    }
    return myFile.equals(todoItem.myFile) &&
           myStartOffset == todoItem.myStartOffset &&
           myEndOffset == todoItem.myEndOffset &&
           myPattern.equals(todoItem.myPattern);
  }
}
