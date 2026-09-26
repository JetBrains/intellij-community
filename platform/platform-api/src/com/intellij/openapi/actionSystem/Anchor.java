// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Defines possible positions of an action relative to another action.
 */
public final class Anchor {
  /**
   * Anchor type that specifies the action to be the first in the list at the
   * moment of addition.
   */
  public static final @NotNull Anchor FIRST  = new Anchor("first");
  /**
   * Anchor type that specifies the action to be the last in the list at the
   * moment of addition.
   */
  public static final @NotNull Anchor LAST   = new Anchor("last");
  /**
   * Anchor type that specifies the action to be placed before the relative
   * action.
   */
  public static final @NotNull Anchor BEFORE = new Anchor("before");
  /**
   * Anchor type that specifies the action to be placed after the relative
   * action.
   */
  public static final @NotNull Anchor AFTER  = new Anchor("after");

  private final String myText;

  private Anchor(@NonNls String text) {
    myText = text;
  }

  @Override
  public String toString() {
    return myText;
  }
}
