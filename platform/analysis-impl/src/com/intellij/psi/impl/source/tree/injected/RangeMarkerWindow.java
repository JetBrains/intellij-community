// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.tree.injected;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class RangeMarkerWindow implements RangeMarkerEx {
  private static final Logger LOG = Logger.getInstance(RangeMarkerWindow.class);
  private final DocumentWindow myDocumentWindow;
  private final RangeMarkerEx myHostMarker;
  private final int myStartShift;
  private final int myEndShift;

  RangeMarkerWindow(@NotNull DocumentWindow documentWindow, int startOffset, int endOffset, boolean surviveOnExternalChange) {
    myDocumentWindow = documentWindow;
    TextRange hostRange = documentWindow.injectedToHost(new ProperTextRange(startOffset, endOffset));
    // shifts to be added to hostToInjected(hostMarker) offsets to get the target marker offsets, when the startOffset/endOffset lie inside prefix/suffix
    myStartShift = startOffset - Math.max(0, documentWindow.hostToInjected(hostRange.getStartOffset()));
    myEndShift = endOffset - Math.max(0, documentWindow.hostToInjected(hostRange.getEndOffset()));
    RangeMarker hostMarker = createHostRangeMarkerToTrack(hostRange, surviveOnExternalChange);
    myHostMarker = (RangeMarkerEx)hostMarker;
    if (documentWindow.isValid() && !isValid()) {
      LOG.error(this + " is invalid immediately after creation");
    }
  }

  @NotNull
  RangeMarker createHostRangeMarkerToTrack(@NotNull TextRange hostRange, boolean surviveOnExternalChange) {
    return myDocumentWindow.getDelegate().createRangeMarker(hostRange.getStartOffset(), hostRange.getEndOffset(), surviveOnExternalChange);
  }

  @Override
  public @NotNull Document getDocument() {
    return myDocumentWindow;
  }

  @Override
  public int getStartOffset() {
    int hostOffset = myHostMarker.getStartOffset();
    return myDocumentWindow.hostToInjected(hostOffset) + myStartShift;
  }

  @Override
  public int getEndOffset() {
    int hostOffset = myHostMarker.getEndOffset();
    return myDocumentWindow.hostToInjected(hostOffset) + myEndShift;
  }

  @Override
  public boolean isValid() {
    if (!myHostMarker.isValid() || !myDocumentWindow.isValid()) return false;
    int startOffset = getStartOffset();
    int endOffset = getEndOffset();
    return startOffset <= endOffset && endOffset <= myDocumentWindow.getTextLength();
  }

  ////////////////////////////delegates
  @Override
  public void setGreedyToLeft(boolean greedy) {
    myHostMarker.setGreedyToLeft(greedy);
  }

  @Override
  public void setGreedyToRight(boolean greedy) {
    myHostMarker.setGreedyToRight(greedy);
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return myHostMarker.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, T value) {
    myHostMarker.putUserData(key, value);
  }

  @Override
  public long getId() {
    return myHostMarker.getId();
  }

  public @NotNull RangeMarkerEx getDelegate() {
    return myHostMarker;
  }

  @Override
  public boolean isGreedyToRight() {
    return myHostMarker.isGreedyToRight();
  }

  @Override
  public boolean isGreedyToLeft() {
    return myHostMarker.isGreedyToLeft();
  }

  @Override
  public void dispose() {
    myHostMarker.dispose();
  }

  @Override
  public String toString() {
    return "RangeMarkerWindow" + (isGreedyToLeft() ? "[" : "(") + (isValid() ? "valid" : "invalid") + "," +
           getStartOffset() + "," + getEndOffset() + 
           (isGreedyToRight() ? "]" : ")") + " " + (isValid() ? getId() : "");
  }
}
