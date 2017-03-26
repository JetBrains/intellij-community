/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.tools.fragmented;

import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.util.LineRange;
import com.intellij.diff.util.Side;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

class UnifiedFragmentBuilder {
  @NotNull private final List<LineFragment> myFragments;
  @NotNull private final Document myDocument1;
  @NotNull private final Document myDocument2;
  @NotNull private final Side myMasterSide;

  @NotNull private final StringBuilder myBuilder = new StringBuilder();
  @NotNull private final List<ChangedBlock> myBlocks = new ArrayList<>();
  @NotNull private final List<HighlightRange> myRanges = new ArrayList<>();
  @NotNull private final LineNumberConvertor.Builder myConvertor1 = new LineNumberConvertor.Builder();
  @NotNull private final LineNumberConvertor.Builder myConvertor2 = new LineNumberConvertor.Builder();
  @NotNull private final List<LineRange> myChangedLines = new ArrayList<>();

  public UnifiedFragmentBuilder(@NotNull List<LineFragment> fragments,
                                @NotNull Document document1,
                                @NotNull Document document2,
                                @NotNull Side masterSide) {
    myFragments = fragments;
    myDocument1 = document1;
    myDocument2 = document2;
    myMasterSide = masterSide;
  }

  private boolean myEqual = false;

  private int lastProcessedLine1 = -1;
  private int lastProcessedLine2 = -1;
  private int totalLines = 0;

  public void exec() {
    if (myFragments.isEmpty()) {
      myEqual = true;
      appendTextMaster(0, 0, getLineCount(myDocument1) - 1, getLineCount(myDocument2) - 1);
      return;
    }

    for (LineFragment fragment : myFragments) {
      processEquals(fragment.getStartLine1() - 1, fragment.getStartLine2() - 1);
      processChanged(fragment);
    }
    processEquals(getLineCount(myDocument1) - 1, getLineCount(myDocument2) - 1);
  }

  private void processEquals(int endLine1, int endLine2) {
    int startLine1 = lastProcessedLine1 + 1;
    int startLine2 = lastProcessedLine2 + 1;

    appendTextMaster(startLine1, startLine2, endLine1, endLine2);
  }

  @SuppressWarnings("UnnecessaryLocalVariable")
  private void processChanged(@NotNull LineFragment fragment) {
    int startLine1 = fragment.getStartLine1();
    int endLine1 = fragment.getEndLine1() - 1;
    int lines1 = endLine1 - startLine1;

    int startLine2 = fragment.getStartLine2();
    int endLine2 = fragment.getEndLine2() - 1;
    int lines2 = endLine2 - startLine2;

    int linesBefore = totalLines;
    int linesAfter;

    if (lines1 >= 0) {
      int startOffset1 = myDocument1.getLineStartOffset(startLine1);
      int endOffset1 = myDocument1.getLineEndOffset(endLine1);
      appendTextSide(Side.LEFT, startOffset1, endOffset1, lines1, lines2, startLine1, -1);
    }

    int linesBetween = totalLines;

    if (lines2 >= 0) {
      int startOffset2 = myDocument2.getLineStartOffset(startLine2);
      int endOffset2 = myDocument2.getLineEndOffset(endLine2);
      appendTextSide(Side.RIGHT, startOffset2, endOffset2, lines2, lines2, -1, startLine2);
    }

    linesAfter = totalLines;

    int blockStartLine1 = linesBefore;
    int blockEndLine1 = linesBetween;
    int blockStartLine2 = linesBetween;
    int blockEndLine2 = linesAfter;

    myBlocks.add(new ChangedBlock(new LineRange(blockStartLine1, blockEndLine1),
                                  new LineRange(blockStartLine2, blockEndLine2),
                                  fragment));

    lastProcessedLine1 = endLine1;
    lastProcessedLine2 = endLine2;
  }

  private void appendTextMaster(int startLine1, int startLine2, int endLine1, int endLine2) {
    // The slave-side line matching might be incomplete for non-fair line fragments (@see FairDiffIterable)
    // If it ever became an issue, it could be fixed by explicit fair by-line comparing of "equal" regions

    int lines1 = endLine1 - startLine1;
    int lines2 = endLine2 - startLine2;
    if (myMasterSide.select(lines1, lines2) >= 0) {
      int startOffset = myMasterSide.isLeft() ? myDocument1.getLineStartOffset(startLine1) : myDocument2.getLineStartOffset(startLine2);
      int endOffset = myMasterSide.isLeft() ? myDocument1.getLineEndOffset(endLine1) : myDocument2.getLineEndOffset(endLine2);

      appendText(myMasterSide, startOffset, endOffset, lines1, lines2, startLine1, startLine2);
    }
  }

  private void appendTextSide(@NotNull Side side, int offset1, int offset2, int lines1, int lines2, int startLine1, int startLine2) {
    int linesBefore = totalLines;
    appendText(side, offset1, offset2, lines1, lines2, startLine1, startLine2);
    int linesAfter = totalLines;

    if (linesBefore != linesAfter) myChangedLines.add(new LineRange(linesBefore, linesAfter));
  }

  private void appendText(@NotNull Side side, int offset1, int offset2, int lines1, int lines2, int startLine1, int startLine2) {
    int lines = side.select(lines1, lines2);
    boolean notEmpty = lines >= 0;

    int appendix = notEmpty ? 1 : 0;
    if (startLine1 != -1) {
      myConvertor1.put(totalLines, startLine1, lines + appendix, lines1 + appendix);
    }
    if (startLine2 != -1) {
      myConvertor2.put(totalLines, startLine2, lines + appendix, lines2 + appendix);
    }

    if (notEmpty) {
      Document document = side.select(myDocument1, myDocument2);

      int newline = document.getTextLength() > offset2 + 1 ? 1 : 0;
      TextRange base = new TextRange(myBuilder.length(), myBuilder.length() + offset2 - offset1 + newline);
      TextRange changed = new TextRange(offset1, offset2 + newline);
      myRanges.add(new HighlightRange(side, base, changed));

      myBuilder.append(document.getCharsSequence().subSequence(offset1, offset2));
      myBuilder.append('\n');

      totalLines += lines + 1;
    }
  }

  private static int getLineCount(@NotNull Document document) {
    return Math.max(document.getLineCount(), 1);
  }

  //
  // Result
  //


  public boolean isEqual() {
    return myEqual;
  }

  @NotNull
  public CharSequence getText() {
    return myBuilder;
  }

  @NotNull
  public List<ChangedBlock> getBlocks() {
    return myBlocks;
  }

  @NotNull
  public List<HighlightRange> getRanges() {
    return myRanges;
  }

  @NotNull
  public LineNumberConvertor getConvertor1() {
    return myConvertor1.build();
  }

  @NotNull
  public LineNumberConvertor getConvertor2() {
    return myConvertor2.build();
  }

  @NotNull
  public List<LineRange> getChangedLines() {
    return myChangedLines;
  }
}
