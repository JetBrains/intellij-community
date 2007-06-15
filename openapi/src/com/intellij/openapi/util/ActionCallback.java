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

  private void callback() {
    if (myDone && myRunnable != null) {
      myRunnable.run();
      consume();
    }
  }

  public final void consume() {
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
