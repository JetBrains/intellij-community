// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;

public final class TimedDeadzone {
  public static final Length DEFAULT = new Length(150);
  public static final Length NULL = new Length(-1);
  
  private Length myLength;
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

  public void setLength(final @NotNull Length deadZone) {
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
