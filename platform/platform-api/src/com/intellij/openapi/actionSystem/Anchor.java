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
 * Defines possible positions of an action relative to another action.
 */

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

  private final String myText;

  private Anchor(@NonNls String text) {
    myText = text;
  }

  public String toString() {
    return myText;
  }
}
