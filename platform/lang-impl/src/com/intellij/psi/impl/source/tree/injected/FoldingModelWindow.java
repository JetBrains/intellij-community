// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.source.tree.injected;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class FoldingModelWindow implements FoldingModelEx, ModificationTracker {
  private final FoldingModelEx myDelegate;
  private final DocumentWindow myDocumentWindow;
  private final EditorWindow myEditorWindow;

  FoldingModelWindow(@NotNull FoldingModelEx delegate, @NotNull DocumentWindow documentWindow, @NotNull EditorWindow editorWindow) {
    myDelegate = delegate;
    myDocumentWindow = documentWindow;
    myEditorWindow = editorWindow;
  }

  @Override
  public void setFoldingEnabled(boolean isEnabled) {
    myDelegate.setFoldingEnabled(isEnabled);
  }

  @Override
  public boolean isFoldingEnabled() {
    return myDelegate.isFoldingEnabled();
  }

  @Override
  public FoldRegion getFoldingPlaceholderAt(@NotNull Point p) {
    return myDelegate.getFoldingPlaceholderAt(p);
  }

  @Override
  public boolean intersectsRegion(int startOffset, int endOffset) {
    int hostStart = myDocumentWindow.injectedToHost(startOffset);
    int hostEnd = myDocumentWindow.injectedToHost(endOffset);
    return myDelegate.intersectsRegion(hostStart, hostEnd);
  }

  @Override
  public FoldRegion addFoldRegion(int startOffset, int endOffset, @NotNull String placeholderText) {
    return createFoldRegion(startOffset, endOffset, placeholderText, null, false);
  }

  @Override
  public void removeFoldRegion(@NotNull FoldRegion region) {
    myDelegate.removeFoldRegion(((FoldingRegionWindow)region).getDelegate());
  }

  @Override
  public FoldRegion @NotNull [] getAllFoldRegions() {
    FoldRegion[] all = myDelegate.getAllFoldRegions();
    List<FoldRegion> result = getWindowRegions(Arrays.asList(all));
    return result.toArray(FoldRegion.EMPTY_ARRAY);
  }

  @Override
  public boolean isOffsetCollapsed(int offset) {
    return myDelegate.isOffsetCollapsed(myDocumentWindow.injectedToHost(offset));
  }

  @Override
  public FoldRegion getCollapsedRegionAtOffset(int offset) {
    FoldRegion host = myDelegate.getCollapsedRegionAtOffset(myDocumentWindow.injectedToHost(offset));
    return host; //todo convert to window?
  }

  @Nullable
  @Override
  public FoldRegion getFoldRegion(int startOffset, int endOffset) {
    TextRange range = new TextRange(startOffset, endOffset);
    TextRange hostRange = myDocumentWindow.injectedToHost(range);
    FoldRegion hostRegion = myDelegate.getFoldRegion(hostRange.getStartOffset(), hostRange.getEndOffset());
    return hostRegion == null ? null : getWindowRegion(hostRegion);
  }
  
  @Nullable
  private FoldingRegionWindow getWindowRegion(@NotNull FoldRegion hostRegion) {
    FoldingRegionWindow window = hostRegion.getUserData(FOLD_REGION_WINDOW);
    return window != null && window.getEditor() == myEditorWindow ? window : null;
  }

  @Override
  public void runBatchFoldingOperation(@NotNull Runnable operation, boolean allowMovingCaret, boolean keepRelativeCaretPosition) {
    myDelegate.runBatchFoldingOperation(operation, allowMovingCaret, keepRelativeCaretPosition);
  }

  @Override
  public @Nullable CustomFoldRegion addCustomLinesFolding(int startLine, int endLine, @NotNull CustomFoldRegionRenderer renderer) {
    Logger.getInstance(FoldingModelWindow.class).error("Custom fold regions aren't supported for injected editors");
    return null;
  }

  @Override
  public void runBatchFoldingOperation(@NotNull Runnable operation, boolean moveCaretFromCollapsedRegion) {
    //noinspection deprecation
    myDelegate.runBatchFoldingOperation(operation, moveCaretFromCollapsedRegion);
  }

  @Override
  public int getLastCollapsedRegionBefore(int offset) {
    return -1; //todo implement
  }

  @Override
  public TextAttributes getPlaceholderAttributes() {
    return myDelegate.getPlaceholderAttributes();
  }

  @Override
  public FoldRegion[] fetchTopLevel() {
    return FoldRegion.EMPTY_ARRAY; //todo implement
  }

  static final Key<FoldingRegionWindow> FOLD_REGION_WINDOW = Key.create("FOLD_REGION_WINDOW");
  @Override
  public FoldRegion createFoldRegion(int startOffset, int endOffset, @NotNull String placeholder, FoldingGroup group, boolean neverExpands) {
    TextRange hostRange = myDocumentWindow.injectedToHost(new TextRange(startOffset, endOffset));
    if (hostRange.getLength() < 2) return null;
    FoldRegion hostRegion = myDelegate.createFoldRegion(hostRange.getStartOffset(), hostRange.getEndOffset(), placeholder, group, neverExpands);
    if (hostRegion == null) return null;
    FoldingRegionWindow window = new FoldingRegionWindow(myDocumentWindow, myEditorWindow, startOffset, endOffset){
      @Override
      @NotNull RangeMarker createHostRangeMarkerToTrack(@NotNull TextRange hostRange, boolean surviveOnExternalChange) {
        return hostRegion;
      }
    };
    hostRegion.putUserData(FOLD_REGION_WINDOW, window);
    return window;
  }

  @Override
  public void addListener(@NotNull FoldingListener listener, @NotNull Disposable parentDisposable) {
    myDelegate.addListener(listener, parentDisposable);
  }

  @Override
  public void rebuild() {
    myDelegate.rebuild();
  }

  @NotNull
  @Override
  public List<FoldRegion> getGroupedRegions(FoldingGroup group) {
    List<FoldRegion> hostRegions = myDelegate.getGroupedRegions(group);
    return getWindowRegions(hostRegions);
  }

  @Override
  public void clearDocumentRangesModificationStatus() {}

  @Override
  public boolean hasDocumentRegionChangedFor(@NotNull FoldRegion region) {
    return false;
  }

  @Override
  public void clearFoldRegions() {
    myDelegate.clearFoldRegions();
  }

  @Override
  public long getModificationCount() {
    return myDelegate instanceof ModificationTracker ? ((ModificationTracker)myDelegate).getModificationCount() : 0;
  }

  @Override
  public @NotNull List<FoldRegion> getRegionsOverlappingWith(int startOffset, int endOffset) {
    int hostStart = myDocumentWindow.injectedToHost(startOffset);
    int hostEnd = myDocumentWindow.injectedToHost(endOffset);
    List<FoldRegion> hostRegions = myDelegate.getRegionsOverlappingWith(hostStart, hostEnd);
    return getWindowRegions(hostRegions);
  }

  private @NotNull List<FoldRegion> getWindowRegions(@NotNull List<? extends FoldRegion> hostRegions) {
    List<FoldRegion> result = new ArrayList<>();
    hostRegions.forEach(hr -> {
      FoldingRegionWindow wr = getWindowRegion(hr);
      if (wr != null) {
        result.add(wr);
      }
    });
    return result;
  }
}
