package com.intellij.localvcs.integration;

public class LocalHistoryActionImpl implements LocalHistoryAction {
  public static LocalHistoryAction NULL = new Null(); // todo try to get rid of this

  private String myName;
  private FileListener myListener;

  public LocalHistoryActionImpl(FileListener l, String name) {
    myName = name;
    myListener = l;
  }

  public void start() {
    myListener.startAction(myName);
  }

  public void finish() {
    myListener.finishAction();
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
