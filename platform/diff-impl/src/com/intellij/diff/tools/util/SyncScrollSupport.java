/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.tools.util;

import com.intellij.diff.util.Side;
import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class SyncScrollSupport {
  private static final Logger LOG = Logger.getInstance(SyncScrollSupport.class);

  public interface SyncScrollable {
    @CalledInAwt
    boolean isSyncScrollEnabled();

    @CalledInAwt
    int transfer(@NotNull Side baseSide, int line);
  }

  public static class TwosideSyncScrollSupport extends SyncScrollSupportBase {
    @NotNull private final List<? extends Editor> myEditors;
    @NotNull private final SyncScrollable myScrollable;

    @NotNull private final ScrollHelper myHelper1;
    @NotNull private final ScrollHelper myHelper2;

    public TwosideSyncScrollSupport(@NotNull List<? extends Editor> editors, @NotNull SyncScrollable scrollable) {
      myEditors = editors;
      myScrollable = scrollable;

      myHelper1 = create(Side.LEFT);
      myHelper2 = create(Side.RIGHT);
    }

    @Override
    @NotNull
    protected List<? extends Editor> getEditors() {
      return myEditors;
    }

    @Override
    @NotNull
    protected List<? extends ScrollHelper> getScrollHelpers() {
      return ContainerUtil.list(myHelper1, myHelper2);
    }

    @NotNull
    public SyncScrollable getScrollable() {
      return myScrollable;
    }

    public void visibleAreaChanged(VisibleAreaEvent e) {
      if (!myScrollable.isSyncScrollEnabled() || isDuringSyncScroll()) return;

      enterDisableScrollSection();
      try {
        if (e.getEditor() == Side.LEFT.select(myEditors)) {
          myHelper1.visibleAreaChanged(e);
        }
        else if (e.getEditor() == Side.RIGHT.select(myEditors)) {
          myHelper2.visibleAreaChanged(e);
        }
      }
      finally {
        exitDisableScrollSection();
      }
    }

    public void makeVisible(@NotNull Side masterSide,
                            int startLine1, int endLine1, int startLine2, int endLine2,
                            final boolean animate) {
      doMakeVisible(masterSide.getIndex(), new int[]{startLine1, startLine2}, new int[]{endLine1, endLine2}, animate);
    }

    @NotNull
    private ScrollHelper create(@NotNull Side side) {
      return ScrollHelper.create(myEditors, side.getIndex(), side.other().getIndex(), myScrollable, side);
    }
  }

  public static class ThreesideSyncScrollSupport extends SyncScrollSupportBase {
    @NotNull private final List<? extends Editor> myEditors;
    @NotNull private final SyncScrollable myScrollable12;
    @NotNull private final SyncScrollable myScrollable23;

    @NotNull private final ScrollHelper myHelper12;
    @NotNull private final ScrollHelper myHelper21;
    @NotNull private final ScrollHelper myHelper23;
    @NotNull private final ScrollHelper myHelper32;

    public ThreesideSyncScrollSupport(@NotNull List<? extends Editor> editors,
                                      @NotNull SyncScrollable scrollable12,
                                      @NotNull SyncScrollable scrollable23) {
      assert editors.size() == 3;

      myEditors = editors;
      myScrollable12 = scrollable12;
      myScrollable23 = scrollable23;

      myHelper12 = create(ThreeSide.LEFT, ThreeSide.BASE);
      myHelper21 = create(ThreeSide.BASE, ThreeSide.LEFT);

      myHelper23 = create(ThreeSide.BASE, ThreeSide.RIGHT);
      myHelper32 = create(ThreeSide.RIGHT, ThreeSide.BASE);
    }

    @Override
    @NotNull
    protected List<? extends Editor> getEditors() {
      return myEditors;
    }

    @Override
    @NotNull
    protected List<? extends ScrollHelper> getScrollHelpers() {
      return ContainerUtil.list(myHelper12, myHelper21, myHelper23, myHelper32);
    }

    @NotNull
    public SyncScrollable getScrollable12() {
      return myScrollable12;
    }

    @NotNull
    public SyncScrollable getScrollable23() {
      return myScrollable23;
    }

    public void visibleAreaChanged(VisibleAreaEvent e) {
      if (isDuringSyncScroll()) return;

      enterDisableScrollSection();
      try {
        if (e.getEditor() == ThreeSide.LEFT.select(myEditors)) {
          if (myScrollable12.isSyncScrollEnabled()) {
            myHelper12.visibleAreaChanged(e);
            if (myScrollable23.isSyncScrollEnabled()) myHelper23.visibleAreaChanged(e);
          }
        }
        else if (e.getEditor() == ThreeSide.BASE.select(myEditors)) {
          if (myScrollable12.isSyncScrollEnabled()) myHelper21.visibleAreaChanged(e);
          if (myScrollable23.isSyncScrollEnabled()) myHelper23.visibleAreaChanged(e);
        }
        else if (e.getEditor() == ThreeSide.RIGHT.select(myEditors)) {
          if (myScrollable23.isSyncScrollEnabled()) {
            myHelper32.visibleAreaChanged(e);
            if (myScrollable12.isSyncScrollEnabled()) myHelper21.visibleAreaChanged(e);
          }
        }
      }
      finally {
        exitDisableScrollSection();
      }
    }

    public void makeVisible(@NotNull ThreeSide masterSide, int[] startLines, int[] endLines, boolean animate) {
      doMakeVisible(masterSide.getIndex(), startLines, endLines, animate);
    }

    @NotNull
    private ScrollHelper create(@NotNull ThreeSide master, @NotNull ThreeSide slave) {
      assert master != slave;
      assert master == ThreeSide.BASE || slave == ThreeSide.BASE;

      boolean leftSide = master == ThreeSide.LEFT || slave == ThreeSide.LEFT;
      SyncScrollable scrollable = leftSide ? myScrollable12 : myScrollable23;

      Side side;
      if (leftSide) {
        // LEFT - BASE -> LEFT
        // BASE - LEFT -> RIGHT
        side = Side.fromLeft(master == ThreeSide.LEFT);
      }
      else {
        // BASE - RIGHT -> LEFT
        // RIGHT - BASE -> RIGHT
        side = Side.fromLeft(master == ThreeSide.BASE);
      }

      return ScrollHelper.create(myEditors, master.getIndex(), slave.getIndex(), scrollable, side);
    }
  }

  //
  // Impl
  //

  private abstract static class SyncScrollSupportBase {
    private int myDuringSyncScrollDepth = 0;

    public boolean isDuringSyncScroll() {
      return myDuringSyncScrollDepth > 0;
    }

    public void enterDisableScrollSection() {
      myDuringSyncScrollDepth++;
    }

    public void exitDisableScrollSection() {
      myDuringSyncScrollDepth--;
      assert myDuringSyncScrollDepth >= 0;
    }

    @NotNull
    protected abstract List<? extends Editor> getEditors();

    @NotNull
    protected abstract List<? extends ScrollHelper> getScrollHelpers();

    protected void doMakeVisible(final int masterIndex, int[] startLines, int[] endLines, final boolean animate) {
      final List<? extends Editor> editors = getEditors();
      final List<? extends ScrollHelper> helpers = getScrollHelpers();

      final int count = editors.size();
      assert startLines.length == count;
      assert endLines.length == count;

      final int[] offsets = getTargetOffsets(editors.toArray(new Editor[count]), startLines, endLines);

      final int[] startOffsets = new int[count];
      for (int i = 0; i < count; i++) {
        startOffsets[i] = editors.get(i).getScrollingModel().getVisibleArea().y;
      }

      final Editor masterEditor = editors.get(masterIndex);
      final int masterOffset = offsets[masterIndex];
      final int masterStartOffset = startOffsets[masterIndex];

      for (ScrollHelper helper : helpers) {
        helper.setAnchor(startOffsets[helper.getMasterIndex()], offsets[helper.getMasterIndex()],
                         startOffsets[helper.getSlaveIndex()], offsets[helper.getSlaveIndex()]);
      }

      doScrollHorizontally(masterEditor, 0, false); // animation will be canceled by "scroll vertically" anyway
      doScrollVertically(masterEditor, masterOffset, animate);

      masterEditor.getScrollingModel().runActionOnScrollingFinished(new Runnable() {
        @Override
        public void run() {
          for (ScrollHelper helper : helpers) {
            helper.removeAnchor();
          }

          int masterFinalOffset = masterEditor.getScrollingModel().getVisibleArea().y;
          boolean animateSlaves = animate && masterFinalOffset == masterStartOffset;
          for (int i = 0; i < count; i++) {
            if (i == masterIndex) continue;
            Editor editor = editors.get(i);

            int finalOffset = editor.getScrollingModel().getVisibleArea().y;
            if (finalOffset != offsets[i]) {
              enterDisableScrollSection();

              doScrollVertically(editor, offsets[i], animateSlaves);

              editor.getScrollingModel().runActionOnScrollingFinished(new Runnable() {
                @Override
                public void run() {
                  exitDisableScrollSection();
                }
              });
            }
          }
        }
      });
    }
  }

  private static abstract class ScrollHelper implements VisibleAreaListener {
    @NotNull private final List<? extends Editor> myEditors;
    private final int myMasterIndex;
    private final int mySlaveIndex;

    @Nullable private Anchor myAnchor;

    public ScrollHelper(@NotNull List<? extends Editor> editors, int masterIndex, int slaveIndex) {
      myEditors = editors;
      myMasterIndex = masterIndex;
      mySlaveIndex = slaveIndex;
    }

    @NotNull
    public static ScrollHelper create(@NotNull List<? extends Editor> editors,
                                      int masterIndex,
                                      int slaveIndex,
                                      @NotNull final SyncScrollable scrollable,
                                      @NotNull final Side side) {
      return new ScrollHelper(editors, masterIndex, slaveIndex) {
        @Override
        protected int convertLine(int value) {
          return scrollable.transfer(side, value);
        }
      };
    }

    protected abstract int convertLine(int value);

    public void setAnchor(int masterStartOffset, int masterEndOffset, int slaveStartOffset, int slaveEndOffset) {
      myAnchor = new Anchor(masterStartOffset, masterEndOffset, slaveStartOffset, slaveEndOffset);
    }

    public void removeAnchor() {
      myAnchor = null;
    }

    @Override
    public void visibleAreaChanged(VisibleAreaEvent e) {
      Rectangle newRectangle = e.getNewRectangle();
      Rectangle oldRectangle = e.getOldRectangle();
      if (oldRectangle == null) return;

      if (newRectangle.x != oldRectangle.x) syncHorizontalScroll(false);
      if (newRectangle.y != oldRectangle.y) syncVerticalScroll(false);
    }

    public int getMasterIndex() {
      return myMasterIndex;
    }

    public int getSlaveIndex() {
      return mySlaveIndex;
    }

    @NotNull
    public Editor getMaster() {
      return myEditors.get(myMasterIndex);
    }

    @NotNull
    public Editor getSlave() {
      return myEditors.get(mySlaveIndex);
    }

    private void syncVerticalScroll(boolean animated) {
      if (getMaster().getDocument().getTextLength() == 0) return;

      Rectangle viewRect = getMaster().getScrollingModel().getVisibleArea();
      int middleY = viewRect.height / 3;

      int offset;
      if (myAnchor == null) {
        LogicalPosition masterPos = getMaster().xyToLogicalPosition(new Point(viewRect.x, viewRect.y + middleY));
        int masterCenterLine = masterPos.line;
        int convertedCenterLine = convertLine(masterCenterLine);

        Point point = getSlave().logicalPositionToXY(new LogicalPosition(convertedCenterLine, masterPos.column));
        int correction = (viewRect.y + middleY) % getMaster().getLineHeight();
        offset = point.y - middleY + correction;
      }
      else {
        double progress = myAnchor.masterStartOffset == myAnchor.masterEndOffset || viewRect.y == myAnchor.masterEndOffset ? 1 :
                          ((double)(viewRect.y - myAnchor.masterStartOffset)) / (myAnchor.masterEndOffset - myAnchor.masterStartOffset);

        offset = myAnchor.slaveStartOffset + (int)((myAnchor.slaveEndOffset - myAnchor.slaveStartOffset) * progress);
      }

      int deltaHeaderOffset = getHeaderOffset(getSlave()) - getHeaderOffset(getMaster());
      doScrollVertically(getSlave(), offset + deltaHeaderOffset, animated);
    }

    private void syncHorizontalScroll(boolean animated) {
      int offset = getMaster().getScrollingModel().getVisibleArea().x;
      doScrollHorizontally(getSlave(), offset, animated);
    }
  }

  private static void doScrollVertically(@NotNull Editor editor, int offset, boolean animated) {
    ScrollingModel model = editor.getScrollingModel();
    if (!animated) model.disableAnimation();
    model.scrollVertically(offset);
    if (!animated) model.enableAnimation();
  }

  private static void doScrollHorizontally(@NotNull Editor editor, int offset, boolean animated) {
    ScrollingModel model = editor.getScrollingModel();
    if (!animated) model.disableAnimation();
    model.scrollHorizontally(offset);
    if (!animated) model.enableAnimation();
  }

  private static int getHeaderOffset(@NotNull final Editor editor) {
    final JComponent header = editor.getHeaderComponent();
    return header == null ? 0 : header.getHeight();
  }

  @NotNull
  private static int[] getTargetOffsets(@NotNull Editor editor1, @NotNull Editor editor2,
                                        int startLine1, int endLine1, int startLine2, int endLine2) {
    return getTargetOffsets(new Editor[]{editor1, editor2},
                            new int[]{startLine1, startLine2},
                            new int[]{endLine1, endLine2});
  }

  @NotNull
  private static int[] getTargetOffsets(@NotNull Editor[] editors, int[] startLines, int[] endLines) {
    int count = editors.length;
    assert startLines.length == count;
    assert endLines.length == count;

    int[] topOffsets = new int[count];
    int[] bottomOffsets = new int[count];
    int[] rangeHeights = new int[count];
    int[] gapLines = new int[count];
    int[] editorHeights = new int[count];
    int[] maximumOffsets = new int[count];
    int[] topShifts = new int[count];

    for (int i = 0; i < count; i++) {
      topOffsets[i] = editors[i].logicalPositionToXY(new LogicalPosition(startLines[i], 0)).y;
      bottomOffsets[i] = editors[i].logicalPositionToXY(new LogicalPosition(endLines[i] + 1, 0)).y;
      rangeHeights[i] = bottomOffsets[i] - topOffsets[i];

      gapLines[i] = 2 * editors[i].getLineHeight();
      editorHeights[i] = editors[i].getScrollingModel().getVisibleArea().height;

      maximumOffsets[i] = ((EditorEx)editors[i]).getScrollPane().getVerticalScrollBar().getMaximum() - editorHeights[i];

      // 'shift' here - distance between editor's top and first line of range

      // make whole range visible. If possible, locate it at 'center' (1/3 of height)
      // If can't show whole range - show as much as we can
      boolean canShow = 2 * gapLines[i] + rangeHeights[i] <= editorHeights[i];

      topShifts[i] = canShow ? Math.min(editorHeights[i] - gapLines[i] - rangeHeights[i], editorHeights[i] / 3) : gapLines[i];
    }

    int topShift = min(topShifts);

    // check if we're at the top of file
    topShift = Math.min(topShift, min(topOffsets));

    int[] offsets = new int[count];
    boolean haveEnoughSpace = true;
    for (int i = 0; i < count; i++) {
      offsets[i] = topOffsets[i] - topShift;
      haveEnoughSpace &= maximumOffsets[i] > offsets[i];
    }

    if (haveEnoughSpace) return offsets;

    // One of the ranges is at end of file - we can't scroll where we want to.
    topShift = 0;
    for (int i = 0; i < count; i++) {
      topShift = Math.max(topOffsets[i] - maximumOffsets[i], topShift);
    }

    for (int i = 0; i < count; i++) {
      // Try to show as much of range as we can (even if it breaks alignment)
      offsets[i] = topOffsets[i] - topShift + Math.max(topShift + rangeHeights[i] + gapLines[i] - editorHeights[i], 0);

      // always show top of the range
      offsets[i] = Math.min(offsets[i], topOffsets[i] - gapLines[i]);
    }

    return offsets;
  }

  private static int min(int[] values) {
    int min = Integer.MAX_VALUE;
    for (int value : values) {
      if (value < min) min = value;
    }
    return min;
  }

  private static class Anchor {
    public final int masterStartOffset;
    public final int masterEndOffset;
    public final int slaveStartOffset;
    public final int slaveEndOffset;

    public Anchor(int masterStartOffset, int masterEndOffset, int slaveStartOffset, int slaveEndOffset) {
      this.masterStartOffset = masterStartOffset;
      this.masterEndOffset = masterEndOffset;
      this.slaveStartOffset = slaveStartOffset;
      this.slaveEndOffset = slaveEndOffset;
    }
  }
}
