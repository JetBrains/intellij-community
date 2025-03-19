// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;

public class PressureShortcut extends MouseShortcut {

  private final double myStage;

  public PressureShortcut(double stage) {
    super(MouseEvent.BUTTON1, 0, 1);
    this.myStage = stage;
  }

  public double getStage() {
    return myStage;
  }

  @Override
  public int getButton() {
    return MouseEvent.BUTTON1;
  }

  @Override
  @JdkConstants.InputEventMask
  public int getModifiers() {
    return 0;
  }

  @Override
  public int getClickCount() {
    return 1;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    PressureShortcut other = (PressureShortcut) obj;

    return myStage == other.myStage;
  }

  @Override
  public int hashCode() {
    return super.hashCode() + (int)myStage;
  }

  @Override
  public boolean startsWith(final @NotNull Shortcut sc) {
    return equals(sc);
  }

  @Override
  public String toString() {
    return "Force touch";
  }
}
