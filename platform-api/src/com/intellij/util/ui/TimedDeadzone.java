package com.intellij.util.ui;

import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

public final class TimedDeadzone {

  public static final Length DEFAULT = new Length(200);
  public static final Length NULL = new Length(-1);
  
  private Alarm myAlarm;
  private Length myLength = NULL;

  private boolean myWithin;

  private Runnable myClear = new Runnable() {
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

    private int myLength;

    public Length(int length) {
      myLength = length;
    }

    public int getLength() {
      return myLength;
    }
  }
}