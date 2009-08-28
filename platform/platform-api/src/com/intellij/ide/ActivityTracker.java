/*
 * @author max
 */
package com.intellij.ide;

public class ActivityTracker {
  private static final ActivityTracker INSTANCE = new ActivityTracker();

  public static ActivityTracker getInstance() {
    return INSTANCE;
  }

  private ActivityTracker() {
  }

  private int ourCount = 0;

  public int getCount() {
    return ourCount;
  }

  public void inc() {
    ourCount++;
  }
}