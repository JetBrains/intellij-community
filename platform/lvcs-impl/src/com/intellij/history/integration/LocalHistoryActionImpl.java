// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration;

import com.intellij.history.ActivityId;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

public final class LocalHistoryActionImpl implements LocalHistoryAction {
  private final LocalHistoryEventDispatcher myDispatcher;
  private final @NlsContexts.Label String myName;
  private final @Nullable ActivityId myActivityId;

  public LocalHistoryActionImpl(LocalHistoryEventDispatcher l, @NlsContexts.Label String name, @Nullable ActivityId activityId) {
    myDispatcher = l;
    myName = name;
    myActivityId = activityId;
  }

  public void start() {
    myDispatcher.startAction();
  }

  @Override
  public void finish() {
    myDispatcher.finishAction(myName, myActivityId);
  }
}
