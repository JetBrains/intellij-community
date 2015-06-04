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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import gnu.trove.TIntFunction;
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

  public static class TwosideSyncScrollSupport {
    @NotNull private final Editor myEditor1;
    @NotNull private final Editor myEditor2;
    @NotNull private final SyncScrollable myScrollable;

    @NotNull private final MyScrollHelper myHelper1;
    @NotNull private final MyScrollHelper myHelper2;

    private boolean myDisabled = false;
    private boolean myDuringSyncScroll = false;

    public TwosideSyncScrollSupport(@NotNull List<? extends Editor> editors, @NotNull SyncScrollable scrollable) {
      this(Side.LEFT.select(editors), Side.RIGHT.select(editors), scrollable);
    }

    public TwosideSyncScrollSupport(@NotNull Editor editor1, @NotNull Editor editor2, @NotNull SyncScrollable scrollable) {
      myEditor1 = editor1;
      myEditor2 = editor2;
      myScrollable = scrollable;

      myHelper1 = create(myEditor1, myEditor2, myScrollable, Side.LEFT);
      myHelper2 = create(myEditor2, myEditor1, myScrollable, Side.RIGHT);
    }

    public boolean isDuringSyncScroll() {
      return myDuringSyncScroll;
    }

    public void setDisabled(boolean value) {
      if (myDisabled == value) LOG.warn(new Throwable("myDisabled == value: " + myDisabled + " - " + value));
      myDisabled = value;
    }

    @NotNull
    public SyncScrollable getScrollable() {
      return myScrollable;
    }

    public void visibleAreaChanged(VisibleAreaEvent e) {
      if (!myScrollable.isSyncScrollEnabled() || myDuringSyncScroll || myDisabled) return;

      myDuringSyncScroll = true;
      try {
        if (e.getEditor() == myEditor1) {
          myHelper1.visibleAreaChanged(e);
        }
        else if (e.getEditor() == myEditor2) {
          myHelper2.visibleAreaChanged(e);
        }
      }
      finally {
        myDuringSyncScroll = false;
      }
    }

    public void makeVisible(@NotNull Side masterSide,
                            int startLine1, int endLine1, int startLine2, int endLine2,
                            final boolean animate) {
      Side slaveSide = masterSide.other();

      final int[] offsets = getTargetOffsets(myEditor1, myEditor2, startLine1, endLine1, startLine2, endLine2);

      final Editor masterEditor = masterSide.select(myEditor1, myEditor2);
      final Editor slaveEditor = slaveSide.select(myEditor1, myEditor2);

      final int masterOffset = masterSide.select(offsets);
      final int slaveOffset = slaveSide.select(offsets);

      int startOffset1 = myEditor1.getScrollingModel().getVisibleArea().y;
      int startOffset2 = myEditor2.getScrollingModel().getVisibleArea().y;
      final int masterStartOffset = masterSide.select(startOffset1, startOffset2);

      myHelper1.setAnchor(startOffset1, offsets[0], startOffset2, offsets[1]);
      myHelper2.setAnchor(startOffset2, offsets[1], startOffset1, offsets[0]);

      doScrollHorizontally(masterEditor, 0, false); // animation will be canceled by "scroll vertically" anyway
      doScrollVertically(masterEditor, masterOffset, animate);

      masterEditor.getScrollingModel().runActionOnScrollingFinished(new Runnable() {
        @Override
        public void run() {
          myHelper1.removeAnchor();
          myHelper2.removeAnchor();

          int masterFinalOffset = masterEditor.getScrollingModel().getVisibleArea().y;
          int slaveFinalOffset = slaveEditor.getScrollingModel().getVisibleArea().y;
          if (slaveFinalOffset != slaveOffset) {
            myDuringSyncScroll = true;

            doScrollVertically(slaveEditor, slaveOffset, animate && masterFinalOffset == masterStartOffset);

            slaveEditor.getScrollingModel().runActionOnScrollingFinished(new Runnable() {
              @Override
              public void run() {
                myDuringSyncScroll = false;
              }
            });
          }
        }
      });
    }
  }

  public static class ThreesideSyncScrollSupport {
    @NotNull private final List<? extends Editor> myEditors;
    @NotNull private final SyncScrollable myScrollable1;
    @NotNull private final SyncScrollable myScrollable2;

    @NotNull private final MyScrollHelper myHelper11;
    @NotNull private final MyScrollHelper myHelper12;
    @NotNull private final MyScrollHelper myHelper21;
    @NotNull private final MyScrollHelper myHelper22;

    private boolean myDisabled = false;
    private boolean myDuringSyncScroll = false;

    public ThreesideSyncScrollSupport(@NotNull List<? extends Editor> editors,
                                      @NotNull SyncScrollable scrollable1,
                                      @NotNull SyncScrollable scrollable2) {
      assert editors.size() == 3;

      myEditors = editors;
      myScrollable1 = scrollable1;
      myScrollable2 = scrollable2;

      myHelper11 = create(editors.get(0), editors.get(1), myScrollable1, Side.LEFT);
      myHelper12 = create(editors.get(1), editors.get(0), myScrollable1, Side.RIGHT);

      myHelper21 = create(editors.get(1), editors.get(2), myScrollable2, Side.LEFT);
      myHelper22 = create(editors.get(2), editors.get(1), myScrollable2, Side.RIGHT);
    }

    public void setDisabled(boolean value) {
      if (myDisabled == value) LOG.warn(new Throwable("myDisabled == value: " + myDisabled + " - " + value));
      myDisabled = value;
    }

    public void visibleAreaChanged(VisibleAreaEvent e) {
      if (myDuringSyncScroll || myDisabled) return;

      myDuringSyncScroll = true;
      try {
        if (e.getEditor() == myEditors.get(0)) {
          if (myScrollable1.isSyncScrollEnabled()) myHelper11.visibleAreaChanged(e);
          if (myScrollable1.isSyncScrollEnabled() && myScrollable2.isSyncScrollEnabled()) myHelper21.visibleAreaChanged(e);
        }
        else if (e.getEditor() == myEditors.get(1)) {
          if (myScrollable1.isSyncScrollEnabled()) myHelper12.visibleAreaChanged(e);
          if (myScrollable2.isSyncScrollEnabled()) myHelper21.visibleAreaChanged(e);
        }
        else if (e.getEditor() == myEditors.get(2)) {
          if (myScrollable2.isSyncScrollEnabled()) myHelper22.visibleAreaChanged(e);
          if (myScrollable2.isSyncScrollEnabled() && myScrollable1.isSyncScrollEnabled()) myHelper12.visibleAreaChanged(e);
        }
      }
      finally {
        myDuringSyncScroll = false;
      }
    }
  }

  @NotNull
  private static MyScrollHelper create(@NotNull Editor master,
                                       @NotNull Editor slave,
                                       @NotNull final SyncScrollable scrollable,
                                       @NotNull final Side side) {
    return new MyScrollHelper(master, slave, new TIntFunction() {
      @Override
      public int execute(int value) {
        return scrollable.transfer(side, value);
      }
    });
  }

  //
  // Impl
  //


  private static class MyScrollHelper implements VisibleAreaListener {
    @NotNull private final Editor myMaster;
    @NotNull private final Editor mySlave;
    @NotNull private final TIntFunction myConvertor;

    @Nullable private Anchor myAnchor;

    public MyScrollHelper(@NotNull Editor master, @NotNull Editor slave, @NotNull TIntFunction convertor) {
      myMaster = master;
      mySlave = slave;
      myConvertor = convertor;
    }

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

    private void syncVerticalScroll(boolean animated) {
      if (myMaster.getDocument().getTextLength() == 0) return;

      Rectangle viewRect = myMaster.getScrollingModel().getVisibleArea();
      int middleY = viewRect.height / 3;

      int offset;
      if (myAnchor == null) {
        LogicalPosition masterPos = myMaster.xyToLogicalPosition(new Point(viewRect.x, viewRect.y + middleY));
        int masterCenterLine = masterPos.line;
        int convertedCenterLine = myConvertor.execute(masterCenterLine);

        Point point = mySlave.logicalPositionToXY(new LogicalPosition(convertedCenterLine, masterPos.column));
        int correction = (viewRect.y + middleY) % myMaster.getLineHeight();
        offset = point.y - middleY + correction;
      }
      else {
        double progress = myAnchor.masterStartOffset == myAnchor.masterEndOffset || viewRect.y == myAnchor.masterEndOffset ? 1 :
                          ((double)(viewRect.y - myAnchor.masterStartOffset)) / (myAnchor.masterEndOffset - myAnchor.masterStartOffset);

        offset = myAnchor.slaveStartOffset + (int)((myAnchor.slaveEndOffset - myAnchor.slaveStartOffset) * progress);
      }

      int deltaHeaderOffset = getHeaderOffset(mySlave) - getHeaderOffset(myMaster);
      doScrollVertically(mySlave, offset + deltaHeaderOffset, animated);
    }

    private void syncHorizontalScroll(boolean animated) {
      int offset = myMaster.getScrollingModel().getVisibleArea().x;
      doScrollHorizontally(mySlave, offset, animated);
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
