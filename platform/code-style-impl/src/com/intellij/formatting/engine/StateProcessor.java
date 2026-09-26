// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.engine;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;

@ApiStatus.Internal
public final class StateProcessor {
  private final Deque<@NotNull State> myStates = new ArrayDeque<>();
  private State myCurrentState;

  public StateProcessor(@NotNull State initial) {
    myCurrentState = initial;
  }

  public void setNextState(@NotNull State state) {
    myStates.add(state);
  }

  public boolean isDone() {
    return myStates.isEmpty() && myCurrentState.isDone();
  }

  public void iteration() {
    if (!myCurrentState.isDone()) {
      myCurrentState.iteration();
    }
    shiftStateIfNecessary();
  }

  private void shiftStateIfNecessary() {
    if (myCurrentState.isDone() && !myStates.isEmpty()) {
      myCurrentState = myStates.removeFirst();
      myCurrentState.prepare();
    }
  }

  public void stop() {
    myCurrentState.stop();
  }
}
