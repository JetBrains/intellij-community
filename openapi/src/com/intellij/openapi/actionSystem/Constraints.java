/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.actionSystem;

/**
 * Represents constraints for some action. Constraints are used to specify
 * action's position in the default group, see {@link DefaultActionGroup}.
 *
 * @see DefaultActionGroup
 */
public class Constraints implements Cloneable {
  /**
   * Anchor.
   */
  public Anchor myAnchor;

  /**
   * Id of the action to be positioned relative to. Used when anchor type
   * is either {@link Anchor#AFTER} or {@link Anchor#BEFORE}.
   *
   */
  public String myRelativeToActionId;

  /**
   * Creates a new constraints instance with the specified anchor type and
   * id of the relative action.
   *
   * @param anchor anchor
   * @param relativeToActionId Id of the relative action
   */
  public Constraints(Anchor anchor, String relativeToActionId){
    myAnchor = anchor;
    myRelativeToActionId = relativeToActionId;
  }

  public Object clone(){
    try{
      return super.clone();
    }
    catch(CloneNotSupportedException exc){
      throw new RuntimeException(exc.getMessage());
    }
  }
}
