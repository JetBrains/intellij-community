// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.search;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternOccurrence;
import org.jetbrains.annotations.NotNull;

import java.util.List;


final class IndexPatternOccurrenceImpl implements IndexPatternOccurrence {
  private final PsiFile myFile;
  private final int myStartOffset;
  private final int myEndOffset;
  private final IndexPattern myPattern;
  private final List<TextRange> myAdditionalRanges;

  IndexPatternOccurrenceImpl(@NotNull PsiFile file, int startOffset, int endOffset,
                                    @NotNull IndexPattern pattern, @NotNull List<TextRange> additionalRanges) {
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
  public @NotNull IndexPattern getPattern() {
    return myPattern;
  }

  @Override
  public int hashCode(){
    return myFile.hashCode()+myStartOffset+myEndOffset+myPattern.hashCode();
  }

  @Override
  public boolean equals(Object obj){
    if(!(obj instanceof IndexPatternOccurrenceImpl todoItem)){
      return false;
    }
    return myFile.equals(todoItem.myFile) &&
           myStartOffset == todoItem.myStartOffset &&
           myEndOffset == todoItem.myEndOffset &&
           myPattern.equals(todoItem.myPattern);
  }
}
