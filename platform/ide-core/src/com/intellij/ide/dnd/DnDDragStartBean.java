// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.dnd;

import java.awt.*;

public class DnDDragStartBean {
  private final Object myAttachedObject;
  private final Point myPoint;


  public DnDDragStartBean(final Object attachedObject) {
    this(attachedObject, null);
  }

  public DnDDragStartBean(final Object attachedObject, final Point point) {
    myAttachedObject = attachedObject;
    myPoint = point;
  }


  public Object getAttachedObject() {
    return myAttachedObject;
  }

  public Point getPoint() {
    return myPoint;
  }

  //If returns <code>true</code> provided "dragging" image won't be shown
  public boolean isEmpty() {
    return false;
  }
}
