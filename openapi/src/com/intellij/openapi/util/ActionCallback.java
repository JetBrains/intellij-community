package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;

public class ActionCallback {

  private boolean myDone;
  private List<Runnable> myRunnables;

  public void setDone() {
    myDone = true;
    callback();
  }

  public final ActionCallback doWhenDone(@NotNull final Runnable runnable) {
    if (myRunnables == null) {
      myRunnables = new ArrayList<Runnable>();
    }

    myRunnables.add(runnable);

    callback();

    return this;
  }

  public final void markDone(final ActionCallback child) {
    doWhenDone(new Runnable() {
      public void run() {
        child.setDone();
      }
    });
  }

  private void callback() {
    if (myDone && myRunnables != null) {
      final Runnable[] all = myRunnables.toArray(new Runnable[myRunnables.size()]);
      myRunnables.clear();
      for (Runnable each : all) {
        each.run();
      }
    }
  }

  public static class Done extends ActionCallback {
    public Done() {
      setDone();
    }
  }
}
