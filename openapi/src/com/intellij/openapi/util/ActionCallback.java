package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

public class ActionCallback {

  private boolean myDone;
  private Runnable myRunnable;

  private boolean myConsumed;

  public final void setDone() {
    if (myConsumed) return;
    myDone = true;
    callback();
  }

  public final void doWhenDone(@NotNull final Runnable runnable) {
    if (myConsumed) return;
    myRunnable = runnable;
    callback();
  }

  public final void setChildDone(final ActionCallback child) {
    doWhenDone(new Runnable() {
      public void run() {
        child.setDone();
      }
    });
  }

  private void callback() {
    if (myDone && myRunnable != null) {
      myRunnable.run();
      consume();
    }
  }

  public void consume() {
    if (myConsumed) return;

    myConsumed = true;
    onConsumed();
  }

  protected void onConsumed() {
  }

  public static class Done extends ActionCallback {
    public Done() {
      setDone();
    }
  }
}
