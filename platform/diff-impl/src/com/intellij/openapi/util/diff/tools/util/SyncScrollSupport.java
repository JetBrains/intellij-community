/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.util.diff.tools.util;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.util.diff.util.CalledInAwt;
import com.intellij.openapi.util.diff.util.Side;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class SyncScrollSupport {
  public interface SyncScrollable {
    @CalledInAwt
    boolean isSyncScrollEnabled();

    @CalledInAwt
    int transfer(@NotNull Side side, int line);
  }

  @NotNull private final Editor myEditor1;
  @NotNull private final Editor myEditor2;
  @NotNull private final SyncScrollable myScrollable;

  @NotNull private final MyScrollHelper myHelper1;
  @NotNull private final MyScrollHelper myHelper2;

  private boolean ourDuringSyncScroll = false;

  public SyncScrollSupport(@NotNull Editor editor1, @NotNull Editor editor2, @NotNull SyncScrollable scrollable) {
    myEditor1 = editor1;
    myEditor2 = editor2;
    myScrollable = scrollable;

    myHelper1 = new MyScrollHelper(Side.LEFT);
    myHelper2 = new MyScrollHelper(Side.RIGHT);
  }

  public void visibleAreaChanged(VisibleAreaEvent e) {
    if (e.getEditor() == myEditor1) {
      myHelper1.visibleAreaChanged(e);
    }
    else if (e.getEditor() == myEditor2) {
      myHelper2.visibleAreaChanged(e);
    }
  }

  private class MyScrollHelper implements VisibleAreaListener {
    @NotNull private final Side mySide;

    public MyScrollHelper(@NotNull Side side) {
      mySide = side;
    }

    @Override
    public void visibleAreaChanged(VisibleAreaEvent e) {
      if (!myScrollable.isSyncScrollEnabled() || ourDuringSyncScroll) return;

      Rectangle newRectangle = e.getNewRectangle();
      Rectangle oldRectangle = e.getOldRectangle();
      if (oldRectangle == null) return;

      ourDuringSyncScroll = true;
      try {
        syncVerticalScroll(newRectangle, oldRectangle);
        syncHorizontalScroll(newRectangle, oldRectangle);
      }
      finally {
        ourDuringSyncScroll = false;
      }
    }

    private void syncVerticalScroll(@NotNull Rectangle newRectangle, @NotNull Rectangle oldRectangle) {
      if (newRectangle.y == oldRectangle.y) return;

      Editor master = getEditor(mySide);
      Editor slave = getEditor(mySide.other());

      if (master.getDocument().getTextLength() == 0) return;

      int masterVerticalScrollOffset = master.getScrollingModel().getVerticalScrollOffset();

      Rectangle viewRect = master.getScrollingModel().getVisibleArea();
      int middleY = viewRect.height / 3;

      LogicalPosition masterPos = master.xyToLogicalPosition(new Point(viewRect.x, masterVerticalScrollOffset + middleY));
      int masterCenterLine = masterPos.line;
      int scrollToLine = myScrollable.transfer(mySide, masterCenterLine);

      int correction = (masterVerticalScrollOffset + middleY) % master.getLineHeight();
      Point point = slave.logicalPositionToXY(new LogicalPosition(scrollToLine, masterPos.column));
      int offset = point.y - middleY + correction;

      int deltaHeaderOffset = getHeaderOffset(slave) - getHeaderOffset(master);
      doScrollVertically(slave.getScrollingModel(), offset + deltaHeaderOffset);
    }

    private void syncHorizontalScroll(@NotNull Rectangle newRectangle, @NotNull Rectangle oldRectangle) {
      if (newRectangle.x == oldRectangle.x) return;

      int offset = newRectangle.x;

      Editor slave = getEditor(mySide.other());
      doScrollHorizontally(slave.getScrollingModel(), offset);
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

  @NotNull
  private Editor getEditor(@NotNull Side side) {
    return side.selectN(myEditor1, myEditor2);
  }

  private static int getLineCount(@NotNull Document document) {
    return Math.max(document.getLineCount(), 1);
  }
}
