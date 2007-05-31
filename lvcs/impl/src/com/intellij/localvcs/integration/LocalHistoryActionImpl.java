package com.intellij.localvcs.integration;

public class LocalHistoryActionImpl implements LocalHistoryAction {
  public static LocalHistoryAction NULL = new Null(); // todo try to get rid of this

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

  private static class Null extends LocalHistoryActionImpl {
    public Null() {
      super(null, null);
    }

    @Override
    public void start() {
    }

    @Override
    public void finish() {
    }
  }
}
