package com.intellij.codeInspection.ex;

/**
 * @author max
 */
public class JobDescriptor {
  private final String myDisplayName;
  private int myTotalAmount;
  private int myDoneAmount;
  public static final JobDescriptor[] EMPTY_ARRAY = new JobDescriptor[0];

  public JobDescriptor(String displayName) {
    myDisplayName = displayName;
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public int getTotalAmount() {
    return myTotalAmount;
  }

  public void setTotalAmount(int totalAmount) {
    myTotalAmount = totalAmount;
  }

  public int getDoneAmount() {
    return myDoneAmount;
  }

  public void setDoneAmount(int doneAmount) {
    myDoneAmount = doneAmount;
  }

  public float getProgress() {
    float localProgress = getDoneAmount();
    if (getTotalAmount() != 0) {
      localProgress /= getTotalAmount();
    } else {
      localProgress = 0;
    }

    return localProgress;
  }
}
