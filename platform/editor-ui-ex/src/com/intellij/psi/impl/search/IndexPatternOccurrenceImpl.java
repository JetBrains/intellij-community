// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.search;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternOccurrence;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
class IndexPatternOccurrenceImpl implements IndexPatternOccurrence {
  private final PsiFile myFile;
  private final int myStartOffset;
  private final int myEndOffset;
  private final IndexPattern myPattern;
  private final List<TextRange> myAdditionalRanges;

  public IndexPatternOccurrenceImpl(@NotNull PsiFile file, int startOffset, int endOffset,
                                    @NotNull IndexPattern pattern, @NotNull List<TextRange> additionalRanges) {
    myFile = file;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myPattern = pattern;
    myAdditionalRanges = additionalRanges;
  }

  @Override
  @NotNull
  public PsiFile getFile() {
    return myFile;
  }

  @Override
  @NotNull
  public TextRange getTextRange() {
    return new TextRange(myStartOffset, myEndOffset);
  }

  @NotNull
  @Override
  public List<TextRange> getAdditionalTextRanges() {
    return myAdditionalRanges;
  }

  @Override
  @NotNull
  public IndexPattern getPattern() {
    return myPattern;
  }

  public int hashCode(){
    return myFile.hashCode()+myStartOffset+myEndOffset+myPattern.hashCode();
  }

  public boolean equals(Object obj){
    if(!(obj instanceof IndexPatternOccurrenceImpl)){
      return false;
    }
    IndexPatternOccurrenceImpl todoItem=(IndexPatternOccurrenceImpl)obj;
    return myFile.equals(todoItem.myFile) &&
           myStartOffset == todoItem.myStartOffset &&
           myEndOffset == todoItem.myEndOffset &&
           myPattern.equals(todoItem.myPattern);
  }
}
