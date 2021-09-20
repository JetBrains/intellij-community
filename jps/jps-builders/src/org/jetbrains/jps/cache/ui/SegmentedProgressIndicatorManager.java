package org.jetbrains.jps.cache.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SegmentedProgressIndicatorManager {
  private static final Object myLock = new Object();
  private final double mySegmentSize;
  private final int myTasksCount;

  public SegmentedProgressIndicatorManager() {
    this(1, 1);
  }

  public SegmentedProgressIndicatorManager(int tasksCount, double segmentSize) {
    mySegmentSize = segmentSize;
    myTasksCount = tasksCount;
  }

  public SubTaskProgressIndicator createSubTaskIndicator() {
    assert myTasksCount != 0;
    return new SubTaskProgressIndicator(this);
  }

  public void updateFraction(double value) {
    double fractionValue = value / myTasksCount * mySegmentSize;

  }

  public void setText(@NotNull Object obj, @Nullable  String text) {

  }

  public void setText2(@NotNull SubTaskProgressIndicator subTask, @Nullable String text) {

  }

  public void finished(Object obj) {
    setText(obj, null);
  }

  //public ProgressIndicator getProgressIndicator() {
  //  return myProgressIndicator;
  //}

  public static final class SubTaskProgressIndicator {
    private final SegmentedProgressIndicatorManager myProgressManager;
    private double myFraction;

    private SubTaskProgressIndicator(SegmentedProgressIndicatorManager progressManager) {
      myProgressManager = progressManager;
      myFraction = 0;
    }

    public void setFraction(double newValue) {
      double diffFraction = newValue - myFraction;
      myProgressManager.updateFraction(diffFraction);
      myFraction = newValue;
    }

    public void setText2(String text) {
      myProgressManager.setText2(this, text);
    }

    public void setText(String text) {
      myProgressManager.setText(this, text);
    }

    public double getFraction() {
      return myFraction;
    }

    public void finished() {
      setFraction(1);
      myProgressManager.setText2(this, null);
    }
  }
}
