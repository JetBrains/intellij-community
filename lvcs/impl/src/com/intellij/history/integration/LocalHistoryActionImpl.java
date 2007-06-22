package com.intellij.history.integration;

import com.intellij.history.LocalHistoryAction;

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
