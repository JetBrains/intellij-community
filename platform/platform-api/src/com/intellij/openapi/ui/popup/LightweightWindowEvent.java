// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  public Balloon asBalloon() {
    return (Balloon)myWindow;
  }

  @NotNull
  public JBPopup asPopup() {
    return (JBPopup)myWindow;
  }
}
