// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;

public final class TooltipEvent {

  private final InputEvent myInputEvent;
  private final boolean myIsEventInsideBalloon;

  private final @Nullable AnAction myAction;
  private final @Nullable AnActionEvent myActionEvent;

  public TooltipEvent(InputEvent inputEvent, boolean isEventInsideBalloon, @Nullable AnAction action, @Nullable AnActionEvent actionEvent) {
    myInputEvent = inputEvent;
    myIsEventInsideBalloon = isEventInsideBalloon;
    myAction = action;
    myActionEvent = actionEvent;
  }

  public InputEvent getInputEvent() {
    return myInputEvent;
  }

  public boolean isIsEventInsideBalloon() {
    return myIsEventInsideBalloon;
  }

  public @Nullable AnAction getAction() {
    return myAction;
  }

  public @Nullable AnActionEvent getActionEvent() {
    return myActionEvent;
  }
}
