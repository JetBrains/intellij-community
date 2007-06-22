package com.intellij.history;

public class Clock {
  private static long myTimestamp = -1;

  public static long getCurrentTimestamp() {
    if (myTimestamp != -1) return myTimestamp;
    return System.currentTimeMillis();
  }

  public static void setCurrentTimestamp(long t) {
    myTimestamp = t;
  }

  public static void useRealClock() {
    myTimestamp = -1;
  }
}
