package com.intellij.history.integration;

public class LocalHistoryActionImpl implements LocalHistoryAction {
  private String myName;
  private EventDispatcher myDispatcher;

  public LocalHistoryActionImpl(EventDispatcher l, String name) {
    myName = name;
    myDispatcher = l;
  }

  public void start() {
    myDispatcher.startAction();
  }

  public void finish() {
    myDispatcher.finishAction(myName);
  }
}
