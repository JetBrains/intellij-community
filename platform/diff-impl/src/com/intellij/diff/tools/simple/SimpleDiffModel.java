// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.simple;

import com.intellij.diff.util.DiffDividerDrawUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.LineRange;
import com.intellij.diff.util.Side;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class SimpleDiffModel {
  @ApiStatus.Internal protected final @NotNull SimpleDiffViewer myViewer;

  private final @NotNull SimpleDiffChangesHolder myDiffChangesHolder = new SimpleDiffChangesHolder();
  private @NotNull ThreeState myIsContentsEqual = ThreeState.UNSURE;

  @ApiStatus.Internal protected final @NotNull List<@Nullable SimpleDiffChangeUi> myPresentations = new ArrayList<>();

  public SimpleDiffModel(@NotNull SimpleDiffViewer viewer) {
    myViewer = viewer;
  }

  public @NotNull ThreeState isContentsEqual() {
    return myIsContentsEqual;
  }

  public @NotNull List<SimpleDiffChange> getChanges() {
    return getChanges(Side.LEFT);
  }

  public @NotNull List<SimpleDiffChange> getChanges(Side side) {
    return myDiffChangesHolder.getValidChanges(side);
  }

  public @NotNull @Unmodifiable List<SimpleDiffChange> getAllChanges() {
    return myDiffChangesHolder.getAllChanges();
  }

  public void setChanges(@NotNull List<? extends SimpleDiffChange> changes, boolean isContentsEqual) {
    clear();

    for (int i = 0; i < changes.size(); i++) {
      SimpleDiffChange change = changes.get(i);
      SimpleDiffChange previousChange = i != 0 ? changes.get(i - 1) : null;

      SimpleDiffChangeUi changeUi = myViewer.createUi(change);
      changeUi.installHighlighter(previousChange);
      myPresentations.add(changeUi);
    }

    myDiffChangesHolder.addAll(changes);
    myIsContentsEqual = ThreeState.fromBoolean(isContentsEqual);
  }

  public void clear() {
    for (SimpleDiffChangeUi changeUi : myPresentations) {
      if (changeUi != null) changeUi.destroyHighlighter();
    }
    myDiffChangesHolder.clear();
    myPresentations.clear();
    myIsContentsEqual = ThreeState.UNSURE;
  }

  public void destroyChange(@NotNull SimpleDiffChange change) {
    SimpleDiffChangeUi changeUi = myPresentations.set(change.getIndex(), null);
    if (changeUi != null) changeUi.destroyHighlighter();

    myDiffChangesHolder.invalidateChanges(List.of(change));
    change.markDestroyed();
  }

  public void updateGutterActions(boolean force) {
    for (SimpleDiffChangeUi changeUi : myPresentations) {
      if (changeUi != null) changeUi.updateGutterActions(force);
    }
  }

  public void handleBeforeDocumentChange(@NotNull Side side, @NotNull DocumentEvent e) {
    if (getChanges().isEmpty()) return;

    LineRange lineRange = DiffUtil.getAffectedLineRange(e);
    int shift = DiffUtil.countLinesShift(e);

    Set<SimpleDiffChange> invalidated = new HashSet<>();
    for (SimpleDiffChange change : getChanges()) {
      if (change.processDocumentChange(lineRange.start, lineRange.end, shift, side)) {
        invalidated.add(change);

        SimpleDiffChangeUi changeUi = myPresentations.get(change.getIndex());
        if (changeUi != null) changeUi.invalidate();
      }
    }

    myDiffChangesHolder.invalidateChanges(invalidated);
  }

  public void paintPolygons(@NotNull Graphics2D g, @NotNull JComponent divider) {
    MyPaintable paintable = new MyPaintable(myPresentations);
    DiffDividerDrawUtil.paintPolygons(g, divider.getWidth(), myViewer.getEditor1(), myViewer.getEditor2(), paintable);
  }

  private static class MyPaintable implements DiffDividerDrawUtil.DividerPaintable {
    private final @NotNull List<@Nullable SimpleDiffChangeUi> myPresentations;
    private MyPaintable(@NotNull List<@Nullable SimpleDiffChangeUi> presentations) {
      myPresentations = presentations;
    }

    @Override
    public void process(@NotNull DiffDividerDrawUtil.DividerPaintable.Handler handler) {
      for (SimpleDiffChangeUi diffChange : myPresentations) {
        if (diffChange == null) continue;
        boolean keepWalk = diffChange.drawDivider(handler);
        if (!keepWalk) return;
      }
    }
  }
}
