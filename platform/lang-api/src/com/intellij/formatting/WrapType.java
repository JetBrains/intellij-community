/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.formatting;

/**
 * Defines possible types of a wrap.
 *
 * @see Wrap#createWrap(WrapType, boolean)
 * @see Wrap#createChildWrap(Wrap, WrapType, boolean)
 */

public enum WrapType {
  /**
   * A line break is always inserted before the start of the element.
   * This corresponds to the "Wrap always" setting in Global Code Style | Wrapping.
   */
  ALWAYS,

  /**
   * A line break is inserted before the start of the element if the right edge
   * of the element goes beyond the specified wrap margin.
   * This corresponds to the "Wrap if long" setting in Global Code Style | Wrapping.
   */
  NORMAL,

  /**
   * A line break is never inserted before the start of the element.
   * This corresponds to the "Do not wrap" setting in Global Code Style | Wrapping.
   */
  NONE,

  /**
   * A line break is inserted before the start of the element if it is a part
   * of list of elements of the same type and at least one of the elements was wrapped.
   * This corresponds to the "Chop down if long" setting in Global Code Style | Wrapping.
   */
  CHOP_DOWN_IF_LONG
}
