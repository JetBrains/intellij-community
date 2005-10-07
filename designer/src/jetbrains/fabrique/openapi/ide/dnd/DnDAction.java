/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.fabrique.openapi.ide.dnd;

import java.awt.*;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragSource;

public interface DnDAction {

  DnDAction ADD  = new Impl("ADD", DnDConstants.ACTION_COPY, DragSource.DefaultCopyDrop, DragSource.DefaultCopyNoDrop);
  DnDAction BIND = new Impl("BIND", DnDConstants.ACTION_LINK, DragSource.DefaultLinkDrop, DragSource.DefaultLinkNoDrop);
  DnDAction MOVE = new Impl("MOVE", DnDConstants.ACTION_MOVE, DragSource.DefaultMoveDrop, DragSource.DefaultMoveNoDrop);


  Cursor getCursor();
  Cursor getRejectCursor();

  int getId();

  final class Impl implements DnDAction {
    private final int myActionId;
    private final String myName;
    private final Cursor myCursor;
    private final Cursor myRejectCursor;

    public Impl(String name, int id, Cursor cursor, Cursor rejectCursor) {
      myName = name;
      myCursor = cursor;
      myRejectCursor = rejectCursor;
      myActionId = id;
    }

    public int getId() {
      return myActionId;
    }

    public Cursor getRejectCursor() {
      return myRejectCursor;
    }

    public Cursor getCursor() {
      return myCursor;
    }

    public String toString() {
      return myName;
    }
  }
}
