package com.intellij.util.ui;

import com.intellij.util.Alarm;

public final class TimedDeadzone {

  private Alarm myAlarm;
  private int myLength = -1;

  private boolean myWithin;

  private Runnable myClear = new Runnable() {
    public void run() {
      clear();
    }
  };

  public TimedDeadzone(int zoneLength, Alarm.ThreadToUse thread) {
    myLength = zoneLength;
    myAlarm = new Alarm(thread);
  }

  public TimedDeadzone(int zoneLength) {
    this(zoneLength, Alarm.ThreadToUse.SWING_THREAD);
  }

  public int getLength() {
    return myLength;
  }

  public void enter() {
    if (!isWithin()) {
      reEnter();
    }
  }

  public void reEnter() {
    if (myLength == -1) {
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
}