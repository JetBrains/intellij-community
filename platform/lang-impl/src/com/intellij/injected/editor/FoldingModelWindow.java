/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.impl.FoldRegionImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
 */
public class FoldingModelWindow implements FoldingModelEx{
  private final FoldingModelEx myDelegate;
  private final DocumentWindow myDocumentWindow;
  private final EditorWindow myEditorWindow;

  public FoldingModelWindow(@NotNull FoldingModelEx delegate, @NotNull DocumentWindow documentWindow, @NotNull EditorWindow editorWindow) {
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
  public FoldRegion getFoldingPlaceholderAt(Point p) {
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
    FoldRegion region = createFoldRegion(startOffset, endOffset, placeholderText, null, false);
    if (region == null) return null;
    if (!addFoldRegion(region)) {
      region.dispose();
      return null;
    }

    return region;
  }

  @Override
  public boolean addFoldRegion(@NotNull final FoldRegion region) {
    return myDelegate.addFoldRegion(((FoldingRegionWindow)region).getDelegate());
  }

  @Override
  public void removeFoldRegion(@NotNull FoldRegion region) {
    myDelegate.removeFoldRegion(((FoldingRegionWindow)region).getDelegate());
  }

  @Override
  @NotNull
  public FoldRegion[] getAllFoldRegions() {
    FoldRegion[] all = myDelegate.getAllFoldRegions();
    List<FoldRegion> result = new ArrayList<FoldRegion>();
    for (FoldRegion region : all) {
      FoldingRegionWindow window = region.getUserData(FOLD_REGION_WINDOW);
      if (window != null && window.getEditor() == myEditorWindow) {
        result.add(window);
      }
    }
    return result.toArray(new FoldRegion[result.size()]);
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

  @Override
  public void runBatchFoldingOperation(@NotNull Runnable operation) {
    myDelegate.runBatchFoldingOperation(operation);
  }

  @Override
  public void runBatchFoldingOperation(@NotNull Runnable operation, boolean moveCaretFromCollapsedRegion) {
    myDelegate.runBatchFoldingOperation(operation, moveCaretFromCollapsedRegion);
  }

  @Override
  public void runBatchFoldingOperationDoNotCollapseCaret(@NotNull Runnable operation) {
    myDelegate.runBatchFoldingOperationDoNotCollapseCaret(operation);
  }

  @Override
  public FoldRegion fetchOutermost(int offset) {
    FoldRegion host = myDelegate.fetchOutermost(myDocumentWindow.injectedToHost(offset));
    return host; //todo convert to window?
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

  private static final Key<FoldingRegionWindow> FOLD_REGION_WINDOW = Key.create("FOLD_REGION_WINDOW");
  @Override
  public FoldRegion createFoldRegion(int startOffset, int endOffset, @NotNull String placeholder, FoldingGroup group, boolean neverExpands) {
    TextRange hostRange = myDocumentWindow.injectedToHost(new TextRange(startOffset, endOffset));
    if (hostRange.getLength() < 2) return null;
    FoldRegion hostRegion = myDelegate.createFoldRegion(hostRange.getStartOffset(), hostRange.getEndOffset(), placeholder, group, neverExpands);
    int startShift = Math.max(0, myDocumentWindow.hostToInjected(hostRange.getStartOffset()) - startOffset);
    int endShift = Math.max(0, endOffset - myDocumentWindow.hostToInjected(hostRange.getEndOffset()) - startShift);
    FoldingRegionWindow window = new FoldingRegionWindow(myDocumentWindow, myEditorWindow, (FoldRegionImpl)hostRegion, startShift, endShift);
    hostRegion.putUserData(FOLD_REGION_WINDOW, window);
    return window;
  }

  @Override
  public boolean addListener(@NotNull FoldingListener listener) {
    return myDelegate.addListener(listener);
  }

  @Override
  public boolean removeListener(@NotNull FoldingListener listener) {
    return myDelegate.removeListener(listener);
  }

  @Override
  public void rebuild() {
    myDelegate.rebuild();
  }
}
