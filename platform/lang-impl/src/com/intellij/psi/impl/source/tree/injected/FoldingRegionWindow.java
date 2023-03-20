// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.impl.FoldRegionImpl;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

public abstract class FoldingRegionWindow extends RangeMarkerWindow implements FoldRegion {
  private final EditorWindow myEditorWindow;

  FoldingRegionWindow(@NotNull DocumentWindow documentWindow,
                      @NotNull EditorWindow editorWindow,
                      int startOffset,
                      int endOffset) {
    super(documentWindow, startOffset, endOffset, false);
    myEditorWindow = editorWindow;
  }
  @Override
  @NotNull abstract RangeMarker createHostRangeMarkerToTrack(@NotNull TextRange hostRange, boolean surviveOnExternalChange);

  @Override
  public boolean isExpanded() {
    return getDelegate().isExpanded();
  }

  @Override
  public void setExpanded(boolean expanded) {
    getDelegate().setExpanded(expanded);
  }

  @Override
  @NotNull
  public String getPlaceholderText() {
    return getDelegate().getPlaceholderText();
  }

  @Override
  public Editor getEditor() {
    return myEditorWindow;
  }

  @Override
  public FoldingGroup getGroup() {
    return getDelegate().getGroup();
  }

  @Override
  public boolean shouldNeverExpand() {
    return false;
  }

  @Override
  public @NotNull FoldRegionImpl getDelegate() {
    return (FoldRegionImpl)super.getDelegate();
  }

  @Override
  public void setGutterMarkEnabledForSingleLine(boolean value) {
    getDelegate().setGutterMarkEnabledForSingleLine(value);
  }

  @Override
  public boolean isGutterMarkEnabledForSingleLine() {
    return getDelegate().isGutterMarkEnabledForSingleLine();
  }

  @Override
  public void setPlaceholderText(@NotNull String text) {
    getDelegate().setPlaceholderText(text);
  }

  public static FoldingRegionWindow getInjectedRegion(@NotNull FoldRegion hostRegion) {
    return hostRegion.getUserData(FoldingModelWindow.FOLD_REGION_WINDOW);
  }
}
