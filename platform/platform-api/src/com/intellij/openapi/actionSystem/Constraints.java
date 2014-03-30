/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.NonNls;

/**
 * Represents constraints for some action. Constraints are used to specify
 * action's position in the default group, see {@link DefaultActionGroup}.
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
  public Constraints(Anchor anchor, @NonNls String relativeToActionId){
    myAnchor = anchor;
    myRelativeToActionId = relativeToActionId;
  }

  @Override
  public Object clone(){
    try{
      return super.clone();
    }
    catch(CloneNotSupportedException exc){
      throw new RuntimeException(exc.getMessage());
    }
  }
}
