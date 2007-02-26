package com.intellij.localvcs;

public class Clock {
  private static Long myTimestamp;

  public static long getCurrentTimestamp() {
    if (myTimestamp != null) return myTimestamp;
    return System.currentTimeMillis();
  }

  public static void setCurrentTimestamp(long t) {
    myTimestamp = t;
  }

  public static void useRealClock() {
    myTimestamp = null;
  }
}
