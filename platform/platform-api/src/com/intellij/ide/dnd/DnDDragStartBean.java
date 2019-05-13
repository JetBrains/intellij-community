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

import java.awt.*;

/**
 * @author mike
 */
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
