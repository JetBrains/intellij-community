package ru.compscicenter.edide.course;

/**
 * author: liana
 * data: 7/28/14.
 */
public class LessonInfo {
  private int myTaskNum;
  private int myTaskFailed;
  private int myTaskSolved;
  private int myTaskUnchecked;

  public int getTaskNum() {
    return myTaskNum;
  }

  public void setTaskNum(int taskNum) {
    myTaskNum = taskNum;
  }

  public int getTaskFailed() {
    return myTaskFailed;
  }

  public void setTaskFailed(int taskFailed) {
    myTaskFailed = taskFailed;
  }

  public int getTaskSolved() {
    return myTaskSolved;
  }

  public void setTaskSolved(int taskSolved) {
    myTaskSolved = taskSolved;
  }

  public int getTaskUnchecked() {
    return myTaskUnchecked;
  }

  public void setTaskUnchecked(int taskUnchecked) {
    myTaskUnchecked = taskUnchecked;
  }
}
