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
package com.intellij.formatting;

import org.jetbrains.annotations.NotNull;

/**
 * The alignment setting for a formatting model block. Blocks which return the same
 * alignment object instance from the {@code getAlignment} method
 * are aligned with each other.
 *
 * @see Block#getAlignment()
 * @see ChildAttributes#getAlignment()
 */

public abstract class Alignment {

  public enum Anchor { LEFT, RIGHT }

  private static AlignmentFactory myFactory;

  static void setFactory(AlignmentFactory factory) {
    myFactory = factory;
  }

  public static Alignment createAlignment() {
    return createAlignment(false, Anchor.LEFT);
  }

  public static Alignment createAlignment(boolean allowBackwardShift) {
    return createAlignment(allowBackwardShift, Anchor.LEFT);
  }

  /**
   * Allows to create {@link Alignment} object with the given settings.
   * <p/>
   * <pre>
   * <ul>
   *   <li>
   *      <b>Allow backward shift</b>
   *      <p/>
   *      Specifies if former aligned element may be shifted to right in order to align to subsequent element.
   *      <p/>
   *      Consider the following example:
   *      <p/>
   *      <pre>
   *          int start  = 1;
   *          int finish = 2;
   *      </pre>
   *      <p/>
   *      Here {@code '='} block of {@code 'int start  = 1'} statement is shifted one symbol right in order to align
   *      to the {@code '='} block of {@code 'int finish  = 1'} statement.
   *   </li>
   *   <li>
   *     <b>Anchor</b>
   *     Identifies how code blocks should be aligned.
   *     <pre>
   *     <ul>
   *         <li>
   *           <b>LEFT:</b>
   *           <p/>
   *           <pre>
   *             int  start  = 1;
   *             long finish = 2;
   *           </pre>
   *         </li>
   *         <li>
   *           <b>RIGHT:</b>
   *           <p/>
   *           <pre>
   *              int  start = 1;
   *             long finish = 2;
   *           </pre>
   *         </li>
   *     </ul>
   *     </pre>
   *   </li>
   * </ul>
   * </pre>
   *
   * @param allowBackwardShift    flag that specifies if former aligned block may be shifted to right in order to align to subsequent
   *                              aligned block
   * @param anchor                alignment anchor
   * @return                      alignment object with the given {@code 'allow backward shift'} setting
   */
  public static Alignment createAlignment(boolean allowBackwardShift, @NotNull Anchor anchor) {
    return myFactory.createAlignment(allowBackwardShift, anchor);
  }
  
  /**
   * Allows to create alignment with the following feature - aligned blocks are aligned to block with the current alignment if the one
   * if found; block with the given {@code 'base'} alignment is checked otherwise.
   * <p/>
   * Example:
   * <p/>
   * <pre>
   *     int i = a ? x
   *               : y;
   * </pre>
   * <p/>
   * Here {@code ':'} is aligned to {@code '?'} and alignment of {@code 'a'} is a {@code 'base alignment'}
   * of {@code '?'} alignment. I.e. the thing is that {@code ':'} is not aligned to {@code 'a'}.
   * <p/>
   * However, we can change example as follows:
   * <p/>
   * <pre>
   *     int i = a
   *             ? x : y;
   * </pre>
   * <p/>
   * Here {@code '?'} is aligned to {@code 'a'} because the later is set as a {@code 'base alignment'} for {@code '?'}.
   * Note that we can't just define the same {@link #createAlignment() simple alignment} for all blocks {@code 'a'},
   * {@code '?'} and {@code ':'} because it would produce formatting like the one below:
   * <p/>
   * <pre>
   *     int i = a ? x
   *             : y;
   * </pre>
   *
   * @param base    base alignment to use within returned alignment object
   * @return        alignment object with the given alignment defined as a {@code 'base alignment'}
   */
  public static Alignment createChildAlignment(final Alignment base) {
    return myFactory.createChildAlignment(base);
  }
}
