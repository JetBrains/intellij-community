/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.actionSystem;

public class Anchor {
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

  private String myText;

  private Anchor(String text) {
    myText = text;
  }

  public String toString() {
    return myText;
  }
}
