// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.engine;

import java.util.ArrayList;
import java.util.List;

public final class StateProcessor {

  private final List<State> myStates = new ArrayList<>();
  private State myCurrentState;

  public StateProcessor(State initial) {
    myCurrentState = initial;
  }

  public void setNextState(State state) {
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
      myCurrentState = myStates.get(0);
      myStates.remove(0);
      myCurrentState.prepare();
    }
  }

  public void stop() {
    myCurrentState.stop();
  }
}
