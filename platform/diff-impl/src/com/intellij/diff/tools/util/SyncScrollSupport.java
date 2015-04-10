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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import gnu.trove.TIntFunction;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class SyncScrollSupport {
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

    public boolean myDuringSyncScroll = false;

    public TwosideSyncScrollSupport(@NotNull Editor editor1, @NotNull Editor editor2, @NotNull SyncScrollable scrollable) {
      myEditor1 = editor1;
      myEditor2 = editor2;
      myScrollable = scrollable;

      myHelper1 = create(myEditor1, myEditor2, myScrollable, Side.LEFT);
      myHelper2 = create(myEditor2, myEditor1, myScrollable, Side.RIGHT);
    }

    public void visibleAreaChanged(VisibleAreaEvent e) {
      if (!myScrollable.isSyncScrollEnabled() || myDuringSyncScroll) return;

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

    @NotNull
    public SyncScrollable getScrollable() {
      return myScrollable;
    }

    public boolean isDuringSyncScroll() {
      return myDuringSyncScroll;
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

    public boolean myDuringSyncScroll = false;

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

    public void visibleAreaChanged(VisibleAreaEvent e) {
      if (myDuringSyncScroll) return;

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

    public MyScrollHelper(@NotNull Editor master, @NotNull Editor slave, @NotNull TIntFunction convertor) {
      myMaster = master;
      mySlave = slave;
      myConvertor = convertor;
    }

    @Override
    public void visibleAreaChanged(VisibleAreaEvent e) {
      Rectangle newRectangle = e.getNewRectangle();
      Rectangle oldRectangle = e.getOldRectangle();
      if (oldRectangle == null) return;

      syncVerticalScroll(newRectangle, oldRectangle);
      syncHorizontalScroll(newRectangle, oldRectangle);
    }

    private void syncVerticalScroll(@NotNull Rectangle newRectangle, @NotNull Rectangle oldRectangle) {
      if (newRectangle.y == oldRectangle.y) return;

      if (myMaster.getDocument().getTextLength() == 0) return;

      int masterVerticalScrollOffset = myMaster.getScrollingModel().getVerticalScrollOffset();

      Rectangle viewRect = myMaster.getScrollingModel().getVisibleArea();
      int middleY = viewRect.height / 3;

      LogicalPosition masterPos = myMaster.xyToLogicalPosition(new Point(viewRect.x, masterVerticalScrollOffset + middleY));
      int masterCenterLine = masterPos.line;
      int scrollToLine = myConvertor.execute(masterCenterLine);

      int correction = (masterVerticalScrollOffset + middleY) % myMaster.getLineHeight();
      Point point = mySlave.logicalPositionToXY(new LogicalPosition(scrollToLine, masterPos.column));
      int offset = point.y - middleY + correction;

      int deltaHeaderOffset = getHeaderOffset(mySlave) - getHeaderOffset(myMaster);
      doScrollVertically(mySlave.getScrollingModel(), offset + deltaHeaderOffset);
    }

    private void syncHorizontalScroll(@NotNull Rectangle newRectangle, @NotNull Rectangle oldRectangle) {
      if (newRectangle.x == oldRectangle.x) return;

      int offset = newRectangle.x;

      doScrollHorizontally(mySlave.getScrollingModel(), offset);
    }
  }

  private static void doScrollVertically(@NotNull ScrollingModel model, int offset) {
    model.disableAnimation();
    try {
      model.scrollVertically(offset);
    }
    finally {
      model.enableAnimation();
    }
  }

  private static void doScrollHorizontally(@NotNull ScrollingModel model, int offset) {
    model.disableAnimation();
    try {
      model.scrollHorizontally(offset);
    }
    finally {
      model.enableAnimation();
    }
  }

  private static int getHeaderOffset(@NotNull final Editor editor) {
    final JComponent header = editor.getHeaderComponent();
    return header == null ? 0 : header.getHeight();
  }
}
