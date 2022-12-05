// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.NonNls;

/**
 * Represents a single constraint for some action.
 * Constraints are used to specify the action's position relative to other actions.
 *
 * @see DefaultActionGroup
 */
public class Constraints implements Cloneable {

  public final static Constraints FIRST = new Constraints(Anchor.FIRST, null);
  public final static Constraints LAST = new Constraints(Anchor.LAST, null);
  /**
   * Anchor.
   */
  public Anchor myAnchor;

  /**
   * The ID of the action to be positioned relative to.
   * Used when the anchor type is either {@link Anchor#AFTER} or {@link Anchor#BEFORE}.
   */
  public String myRelativeToActionId;

  /**
   * Creates a new Constraints instance with the specified anchor type and
   * the ID of the relative action.
   *
   * @param anchor             anchor
   * @param relativeToActionId ID of the relative action,
   *                           for {@link Anchor#BEFORE} or {@link Anchor#AFTER}
   */
  public Constraints(Anchor anchor, @NonNls String relativeToActionId) {
    myAnchor = anchor;
    myRelativeToActionId = relativeToActionId;
  }

  @Override
  public Object clone() {
    try {
      return super.clone();
    }
    catch (CloneNotSupportedException exc) {
      throw new RuntimeException(exc.getMessage());
    }
  }
}
