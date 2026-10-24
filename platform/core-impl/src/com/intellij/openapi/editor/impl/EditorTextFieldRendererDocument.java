// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.DocumentText;
import com.intellij.openapi.editor.ex.LineIterator;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Read-only document optimized for rendering of millions of EditorTextFields.
 * The only mutating method is setText() which is extremely cheap.
 */
public class EditorTextFieldRendererDocument extends UserDataHolderBase implements DocumentEx {
  private final RangeMarkerTree<RangeMarkerEx> myRangeMarkers = new RangeMarkerTree<>(this);
  private DocumentText myText = DocumentText.createText("");

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
    myText = DocumentText.createText(s);
  }

  @Override
  public int getLineSeparatorLength(int line) {
    return myText.lineSeparatorLength(line);
  }

  @Override
  public @NotNull LineIterator createLineIterator() {
    return myText.lineIterator();
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
    return myText.chars();
  }

  @Override
  public int getLineCount() {
    return myText.lineCount();
  }

  @Override
  public int getLineNumber(int offset) {
    return myText.lineNumber(offset);
  }

  @Override
  public int getLineStartOffset(int line) {
    return myText.lineStartOffset(line);
  }

  @Override
  public int getLineEndOffset(int line) {
    return myText.lineEndOffset(line);
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

  @ApiStatus.Experimental
  @Override
  public @NotNull DocumentText getDocumentText() {
    return myText;
  }

  @Override
  public String toString() {
    return "EditorTextFieldRendererDocument{myLength = "+ myText.length() + "; " + "myRangeMarkers = " + myRangeMarkers.size() +"}";
  }
}
