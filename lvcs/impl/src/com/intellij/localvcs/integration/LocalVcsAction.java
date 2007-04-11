package com.intellij.localvcs.integration;

public class LocalVcsAction implements ILocalVcsAction {
  public static LocalVcsAction NULL = new Null(); // todo try to get rid of this

  private String myName;
  private FileListener myListener;

  public LocalVcsAction(FileListener l, String name) {
    myName = name;
    myListener = l;
  }

  public void start() {
    myListener.startAction(myName);
  }

  public void finish() {
    myListener.finishAction();
  }

  private static class Null extends LocalVcsAction {
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
