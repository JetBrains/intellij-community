// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.simple;

import com.intellij.diff.util.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

public class SimpleDiffModel {
  @NotNull private final SimpleDiffViewer myViewer;

  @NotNull private final List<SimpleDiffChange> myValidChanges = new ArrayList<>();
  @NotNull private final List<SimpleDiffChange> myAllChanges = new ArrayList<>();
  @NotNull private ThreeState myIsContentsEqual = ThreeState.UNSURE;

  @NotNull private final List<SimpleDiffChangeUi> myPresentations = new ArrayList<>();

  @NotNull private final SimpleAlignedDiffModel myAlignedDiffModel;

  public SimpleDiffModel(@NotNull SimpleDiffViewer viewer) {
    myViewer = viewer;
    myAlignedDiffModel = new SimpleAlignedDiffModel(viewer);
  }

  @NotNull
  public ThreeState isContentsEqual() {
    return myIsContentsEqual;
  }

  @NotNull
  public List<SimpleDiffChange> getChanges() {
    return Collections.unmodifiableList(myValidChanges);
  }

  @NotNull
  public List<SimpleDiffChange> getAllChanges() {
    return ContainerUtil.filter(myAllChanges, it -> !it.isDestroyed());
  }

  public void setChanges(@NotNull List<SimpleDiffChange> changes, boolean isContentsEqual) {
    clear();

    for (int i = 0; i < changes.size(); i++) {
      SimpleDiffChange change = changes.get(i);
      SimpleDiffChange previousChange = i != 0 ? changes.get(i - 1) : null;

      SimpleDiffChangeUi changeUi = myViewer.createUi(change);
      changeUi.installHighlighter(previousChange);
      myAlignedDiffModel.alignChange(change);
      myPresentations.add(changeUi);
    }

    myValidChanges.addAll(changes);
    myAllChanges.addAll(changes);
    myIsContentsEqual = ThreeState.fromBoolean(isContentsEqual);
  }

  public void clear() {
    for (SimpleDiffChangeUi changeUi : myPresentations) {
      if (changeUi != null) changeUi.destroyHighlighter();
    }
    myValidChanges.clear();
    myAllChanges.clear();
    myPresentations.clear();
    myAlignedDiffModel.clear();
    myIsContentsEqual = ThreeState.UNSURE;
  }

  public void destroyChange(@NotNull SimpleDiffChange change) {
    SimpleDiffChangeUi changeUi = myPresentations.set(change.getIndex(), null);
    if (changeUi != null) changeUi.destroyHighlighter();

    myValidChanges.remove(change);
    change.markDestroyed();
  }

  public void updateGutterActions(boolean force) {
    for (SimpleDiffChangeUi changeUi : myPresentations) {
      if (changeUi != null) changeUi.updateGutterActions(force);
    }
  }

  public void handleBeforeDocumentChange(@NotNull Side side, @NotNull DocumentEvent e) {
    if (myValidChanges.isEmpty()) return;

    LineRange lineRange = DiffUtil.getAffectedLineRange(e);
    int shift = DiffUtil.countLinesShift(e);

    Set<SimpleDiffChange> invalidated = new HashSet<>();
    for (SimpleDiffChange change : myValidChanges) {
      if (change.processDocumentChange(lineRange.start, lineRange.end, shift, side)) {
        invalidated.add(change);

        SimpleDiffChangeUi changeUi = myPresentations.get(change.getIndex());
        if (changeUi != null) changeUi.invalidate();
      }
    }

    myValidChanges.removeAll(invalidated);
  }

  public void paintPolygons(@NotNull Graphics2D g, @NotNull JComponent divider) {
    MyPaintable paintable = new MyPaintable(getChanges(), myViewer.needAlignChanges());
    DiffDividerDrawUtil.paintPolygons(g, divider.getWidth(), myViewer.getEditor1(), myViewer.getEditor2(), paintable);
  }

  private static class MyPaintable implements DiffDividerDrawUtil.DividerPaintable {
    private final List<SimpleDiffChange> myChanges;
    private final boolean myNeedAlignChanges;

    private MyPaintable(@NotNull List<SimpleDiffChange> changes, boolean alignChanges) {
      myChanges = changes;
      myNeedAlignChanges = alignChanges;
    }

    @Override
    public void process(@NotNull DiffDividerDrawUtil.DividerPaintable.Handler handler) {
      for (SimpleDiffChange diffChange : myChanges) {
        int startLine1 = diffChange.getStartLine(Side.LEFT);
        int endLine1 = diffChange.getEndLine(Side.LEFT);
        int startLine2 = diffChange.getStartLine(Side.RIGHT);
        int endLine2 = diffChange.getEndLine(Side.RIGHT);
        TextDiffType type = diffChange.getDiffType();

        if (myNeedAlignChanges) {
          if (!handler.processAligned(startLine1, endLine1, startLine2, endLine2, type)) {
            return;
          }
        }
        else if (!handler.processExcludable(startLine1, endLine1,
                                            startLine2, endLine2,
                                            type, diffChange.isExcluded(), diffChange.isSkipped())) {
          return;
        }
      }
    }
  }
}
