// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.simple;

import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.LineRange;
import com.intellij.diff.util.Side;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SimpleDiffModel {
  @NotNull private final SimpleDiffViewer myViewer;

  @NotNull private final List<SimpleDiffChange> myValidChanges = new ArrayList<>();
  @NotNull private final List<SimpleDiffChange> myAllChanges = new ArrayList<>();
  @NotNull private ThreeState myIsContentsEqual = ThreeState.UNSURE;

  @NotNull private final List<SimpleDiffChangeUi> myPresentations = new ArrayList<>();

  public SimpleDiffModel(@NotNull SimpleDiffViewer viewer) {
    myViewer = viewer;
  }

  @NotNull
  public ThreeState isContentsEqual() {
    return myIsContentsEqual;
  }

  @NotNull
  public List<SimpleDiffChange> getChanges() {
    return myValidChanges;
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
}
