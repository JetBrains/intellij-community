/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.injected.editor;

import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.impl.FoldRegionImpl;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author cdr
 */
public class FoldingModelWindow implements FoldingModelEx{
  private final FoldingModelEx myDelegate;
  private final DocumentWindow myDocumentWindow;

  public FoldingModelWindow(FoldingModelEx delegate, DocumentWindow documentWindow) {
    myDelegate = delegate;
    myDocumentWindow = documentWindow;
  }

  public void setFoldingEnabled(boolean isEnabled) {
    myDelegate.setFoldingEnabled(isEnabled);
  }

  public boolean isFoldingEnabled() {
    return myDelegate.isFoldingEnabled();
  }

  public FoldRegion getFoldingPlaceholderAt(Point p) {
    return myDelegate.getFoldingPlaceholderAt(p);
  }

  public FoldRegion[] getAllFoldRegionsIncludingInvalid() {
    return myDelegate.getAllFoldRegionsIncludingInvalid();
  }

  public boolean intersectsRegion(int startOffset, int endOffset) {
    int hostStart = myDocumentWindow.injectedToHost(startOffset);
    int hostEnd = myDocumentWindow.injectedToHost(endOffset);
    return myDelegate.intersectsRegion(hostStart, hostEnd);
  }

  public FoldRegion addFoldRegion(int startOffset, int endOffset, @NotNull String placeholderText) {
    return myDelegate.addFoldRegion(myDocumentWindow.injectedToHost(startOffset), myDocumentWindow.injectedToHost(endOffset), placeholderText);
  }

  public boolean addFoldRegion(@NotNull final FoldRegion region) {
    return myDelegate.addFoldRegion(new FoldRegionImpl(region.getEditor(), myDocumentWindow.injectedToHost(region.getStartOffset()),
                                                       myDocumentWindow.injectedToHost(region.getEndOffset()), region.getPlaceholderText(),
                                                       region.getGroup()));
  }

  public void removeFoldRegion(@NotNull FoldRegion region) {
    myDelegate.removeFoldRegion(region);
  }

  @NotNull
  public FoldRegion[] getAllFoldRegions() {
    return myDelegate.getAllFoldRegions();
  }

  public boolean isOffsetCollapsed(int offset) {
    return myDelegate.isOffsetCollapsed(myDocumentWindow.injectedToHost(offset));
  }

  public FoldRegion getCollapsedRegionAtOffset(int offset) {
    return myDelegate.getCollapsedRegionAtOffset(myDocumentWindow.injectedToHost(offset));
  }

  public void runBatchFoldingOperation(@NotNull Runnable operation) {
    myDelegate.runBatchFoldingOperation(operation);
  }

  public void runBatchFoldingOperationDoNotCollapseCaret(@NotNull Runnable operation) {
    myDelegate.runBatchFoldingOperationDoNotCollapseCaret(operation);
  }

  public FoldRegion fetchOutermost(int offset) {
    return null;
  }

  public int getLastCollapsedRegionBefore(int offset) {
    return -1;
  }

  public TextAttributes getPlaceholderAttributes() {
    return myDelegate.getPlaceholderAttributes();
  }

  public FoldRegion[] fetchTopLevel() {
    return FoldRegion.EMPTY_ARRAY;
  }
}
