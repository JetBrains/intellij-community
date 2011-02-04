/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
 * @author Konstantin Bulenkov
 */
public final class DnDActionInfo {
  private final DnDAction myAction;
  private final Point myPoint;

  public DnDActionInfo(DnDAction action, Point dragOrigin) {
    myAction = action;
    myPoint = dragOrigin;
  }

  public DnDAction getAction() {
    return myAction;
  }

  public Point getPoint() {
    return myPoint;
  }

  public boolean isMove() {
    return myAction == DnDAction.MOVE;
  }

  public boolean isCopy() {
    return myAction == DnDAction.COPY;
  }

  public boolean isLink() {
    return myAction == DnDAction.LINK;
  }
}
