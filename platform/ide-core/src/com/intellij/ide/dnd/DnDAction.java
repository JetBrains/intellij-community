/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.dnd;

import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragSource;

public enum DnDAction {

  COPY("COPY", DnDConstants.ACTION_COPY, DragSource.DefaultCopyDrop, DragSource.DefaultCopyNoDrop),
  LINK("LINK", DnDConstants.ACTION_LINK, DragSource.DefaultLinkDrop, DragSource.DefaultLinkNoDrop),
  MOVE("MOVE", DnDConstants.ACTION_MOVE, DragSource.DefaultMoveDrop, DragSource.DefaultMoveNoDrop);
  
  //ADD("ADD", DnDConstants.ACTION_COPY, DragSource.DefaultCopyDrop, DragSource.DefaultCopyNoDrop),
  //BIND("BIND", DnDConstants.ACTION_LINK, DragSource.DefaultLinkDrop, DragSource.DefaultLinkNoDrop),
  //MOVE("MOVE", DnDConstants.ACTION_MOVE, DragSource.DefaultMoveDrop, DragSource.DefaultMoveNoDrop);


  String myName;
  int myActionId;
  Cursor myCursor;
  Cursor myRejectCursor;


  DnDAction(final @NonNls String name, final int actionId, final Cursor cursor, final Cursor rejectCursor) {
   myName = name;
   myActionId = actionId;
   myCursor = cursor;
   myRejectCursor = rejectCursor;
 }


  public String getName() {
    return myName;
  }

  public int getActionId() {
    return myActionId;
  }

  public Cursor getCursor() {
    return myCursor;
  }

  public Cursor getRejectCursor() {
    return myRejectCursor;
  }
}
