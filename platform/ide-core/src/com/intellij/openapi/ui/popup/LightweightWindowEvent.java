// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.popup;

import org.jetbrains.annotations.NotNull;

public class LightweightWindowEvent {

  private final LightweightWindow myWindow;
  private final boolean myOk;

  public LightweightWindowEvent(@NotNull LightweightWindow window) {
    this(window, false);
  }

  public LightweightWindowEvent(@NotNull LightweightWindow window, boolean isOk) {
    myWindow = window;
    myOk = isOk;
  }

  public boolean isOk() {
    return myOk;
  }

  public @NotNull Balloon asBalloon() {
    return (Balloon)myWindow;
  }

  public @NotNull JBPopup asPopup() {
    return (JBPopup)myWindow;
  }
}
