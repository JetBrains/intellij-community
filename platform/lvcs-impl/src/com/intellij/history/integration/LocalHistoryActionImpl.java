// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration;

import com.intellij.history.LocalHistoryAction;

public class LocalHistoryActionImpl implements LocalHistoryAction {
  private final String myName;
  private final LocalHistoryEventDispatcher myDispatcher;

  public LocalHistoryActionImpl(LocalHistoryEventDispatcher l, String name) {
    myName = name;
    myDispatcher = l;
  }

  public void start() {
    myDispatcher.startAction();
  }

  @Override
  public void finish() {
    myDispatcher.finishAction(myName);
  }
}
