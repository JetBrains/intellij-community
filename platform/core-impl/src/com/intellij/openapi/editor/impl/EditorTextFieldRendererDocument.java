// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.LineIterator;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * Read-only document optimized for rendering of millions of EditorTextFields.
 * The only mutating method is setText() which is extremely cheap.
 */
public class EditorTextFieldRendererDocument extends UserDataHolderBase implements DocumentEx {
  private final RangeMarkerTree<RangeMarkerEx> myRangeMarkers = new RangeMarkerTree<>(this);
  private char[] myChars = ArrayUtilRt.EMPTY_CHAR_ARRAY;
  private String myString = "";
  private LineSet myLineSet = LineSet.createLineSet(myString);

  @Override
  public void setModificationStamp(long modificationStamp) {
  }

  @Override
  public void replaceText(@NotNull CharSequence chars, long newModificationStamp) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setText(@NotNull CharSequence text) {
    String s = StringUtil.convertLineSeparators(text.toString());
    myChars = new char[s.length()];
    s.getChars(0, s.length(), myChars, 0);
    myString = new String(myChars);
    myLineSet = LineSet.createLineSet(myString);
  }

  @Override
  public int getLineSeparatorLength(int line) {
    int separatorLength = myLineSet.getSeparatorLength(line);
    assert separatorLength >= 0;
    return separatorLength;
  }

  @Override
  public @NotNull LineIterator createLineIterator() {
    return myLineSet.createIterator();
  }

  @Override
  public boolean removeRangeMarker(@NotNull RangeMarkerEx rangeMarker) { return myRangeMarkers.removeInterval(rangeMarker); }

  @Override
  public void registerRangeMarker(@NotNull RangeMarkerEx rangeMarker,
                                  int start,
                                  int end,
                                  boolean greedyToLeft,
                                  boolean greedyToRight,
                                  int layer) {
    myRangeMarkers.addInterval(rangeMarker, start, end, greedyToLeft, greedyToRight, false, layer);
  }

  @Override
  public boolean processRangeMarkers(@NotNull Processor<? super RangeMarker> processor) { return myRangeMarkers.processAll(processor); }

  @Override
  public boolean processRangeMarkersOverlappingWith(int start,
                                                    int end,
                                                    @NotNull Processor<? super RangeMarker> processor) {
    return myRangeMarkers.processOverlappingWith(start, end, processor);
  }

  @Override
  public @NotNull CharSequence getImmutableCharSequence() {
    return myString;
  }

  @Override
  public char @NotNull [] getChars() { return myChars; }

  @Override
  public int getLineCount() { return myLineSet.findLineIndex(myChars.length) + 1; }

  @Override
  public int getLineNumber(int offset) { return myLineSet.findLineIndex(offset); }

  @Override
  public int getLineStartOffset(int line) { return myChars.length == 0 ? 0 : myLineSet.getLineStart(line); }

  @Override
  public int getLineEndOffset(int line) {
    if (getTextLength() == 0 && line == 0) return 0;
    int result = myLineSet.getLineEnd(line) - getLineSeparatorLength(line);
    assert result >= 0;
    return result;
  }

  @Override
  public void insertString(int offset, @NotNull CharSequence s) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void deleteString(int startOffset, int endOffset) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void replaceString(int startOffset, int endOffset, @NotNull CharSequence s) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public boolean isWritable() {
    return false;
  }

  @Override
  public long getModificationStamp() {
    return 0;
  }

  @Override
  public @NotNull RangeMarker createRangeMarker(int startOffset, int endOffset, boolean surviveOnExternalChange) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public @NotNull RangeMarker createGuardedBlock(int startOffset, int endOffset) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public String toString() {
    return "EditorTextFieldRendererDocument{myLength = "+ myChars.length + "; " + "myRangeMarkers = " + myRangeMarkers.size() +"}";
  }
}
