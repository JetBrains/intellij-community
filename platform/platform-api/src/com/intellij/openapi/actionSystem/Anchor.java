// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.NonNls;

/**
 * Defines possible positions of an action relative to another action.
 */

public final class Anchor {
  /**
   * Anchor type that specifies the action to be the first in the list at the
   * moment of addition.
   */
  public static final Anchor FIRST  = new Anchor("first");
  /**
   * Anchor type that specifies the action to be the last in the list at the
   * moment of addition.
   */
  public static final Anchor LAST   = new Anchor("last");
  /**
   * Anchor type that specifies the action to be placed before the relative
   * action.
   */
  public static final Anchor BEFORE = new Anchor("before");
  /**
   * Anchor type that specifies the action to be placed after the relative
   * action.
   */
  public static final Anchor AFTER  = new Anchor("after");

  private final String myText;

  private Anchor(@NonNls String text) {
    myText = text;
  }

  public String toString() {
    return myText;
  }
}
