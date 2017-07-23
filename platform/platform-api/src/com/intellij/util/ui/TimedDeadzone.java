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
package com.intellij.util.ui;

import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;

public final class TimedDeadzone {
  public static final Length DEFAULT = new Length(150);
  public static final Length NULL = new Length(-1);
  
  private Length myLength = NULL;
  private boolean myMouseWithin;
  private long myTimeEntered = -1;

  public TimedDeadzone(Length zoneLength) {
    myLength = zoneLength;
  }

  public int getLength() {
    return myLength.getLength();
  }

  public void enter(MouseEvent e) {
    if (myMouseWithin) {
      return;
    }

    myTimeEntered = e.getWhen();
    myMouseWithin = true;
  }

  public void clear() {
    myMouseWithin = false;
  }

  public boolean isWithin() {
    long now = System.currentTimeMillis();
    return myMouseWithin && now > myTimeEntered && now - myTimeEntered < getLength();
  }

  public void setLength(@NotNull final Length deadZone) {
    myLength = deadZone;
  }

  public static class Length {

    private final int myLength;

    public Length(int length) {
      myLength = length;
    }

    public int getLength() {
      return myLength;
    }
  }
}
