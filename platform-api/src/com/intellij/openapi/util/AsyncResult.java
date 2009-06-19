package com.intellij.openapi.util;

public class AsyncResult<T> extends ActionCallback {

  private T myResult;

  public void setDone(T result) {
    myResult = result;
    super.setDone();
  }

  public AsyncResult<T> doWhenDone(final Handler<T> handler) {
    doWhenDone(new Runnable() {
      public void run() {
        handler.run(myResult);
      }
    });
    return this;
  }

  public static interface Handler<T> {
    void run(T t);
  }

  public static class Done<T> extends AsyncResult<T> {
    public Done(T value) {
      setDone(value);
    }
  }

  public static class Rejected<T> extends AsyncResult<T> {
    public Rejected() {
      setRejected();
    }
  }

}