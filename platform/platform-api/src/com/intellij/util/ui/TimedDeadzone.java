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

import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

public final class TimedDeadzone {

  public static final Length DEFAULT = new Length(150);
  public static final Length NULL = new Length(-1);
  
  private Alarm myAlarm;
  private Length myLength = NULL;

  private boolean myWithin;

  private final Runnable myClear = new Runnable() {
    public void run() {
      clear();
    }
  };

  public TimedDeadzone(Length zoneLength, Alarm.ThreadToUse thread) {
    myLength = zoneLength;
    myAlarm = new Alarm(thread);
  }

  public TimedDeadzone(Length zoneLength) {
    this(zoneLength, Alarm.ThreadToUse.SWING_THREAD);
  }

  public int getLength() {
    return myLength.getLength();
  }

  public void enter() {
    if (!isWithin()) {
      reEnter();
    }
  }

  public void reEnter() {
    if (myLength == NULL) {
      clear();
      return;
    }

    myAlarm.cancelAllRequests();
    myWithin = true;
    myAlarm.addRequest(myClear, getLength());
  }

  public void clear() {
    myAlarm.cancelAllRequests();
    myWithin = false;
  }

  public boolean isWithin() {
    return myWithin;
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
