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

/**
 * The alignment setting for a formatting model block. Blocks which return the same
 * alignment object instance from the <code>getAlignment</code> method
 * are aligned with each other.
 *
 * @see com.intellij.formatting.Block#getAlignment()
 * @see com.intellij.formatting.ChildAttributes#getAlignment()
 */

public abstract class Alignment {
  private static AlignmentFactory myFactory;

  static void setFactory(AlignmentFactory factory) {
    myFactory = factory;
  }

  /**
   * Shorthand for calling {@link #createAlignment(boolean)} with <code>'false'</code>.
   *
   * @return      alignment object with default settings
   */
  public static Alignment createAlignment() {
    return myFactory.createAlignment(false);
  }

  /**
   * Specifies if former aligned element may be shifted to right in order to align to subsequent element.
   * <p/>
   * Consider the following example:
   * <p/>
   * <pre>
   *     int start  = 1;
   *     int finish = 2;
   * </pre>
   * <p/>
   * Here <code>'='</code> block of <code>'int start  = 1'</code> statement is shifted one symbol right in order to align
   * to the <code>'='</code> block of <code>'int finish  = 1'</code> statement.
   *
   * @param allowBackwardShift    flag that specifies if former aligned block may be shifted to right in order to align to subsequent
   *                              aligned block
   * @return                      alignment object with the given <code>'allow backward shift'</code> setting
   */
  public static Alignment createAlignment(boolean allowBackwardShift) {
    return myFactory.createAlignment(allowBackwardShift);
  }

  /**
   * Allows to create alignment with the following feature - aligned blocks are aligned to block with the current alignment if the one
   * if found; block with the given <code>'base'</code> alignment is checked otherwise.
   * <p/>
   * Example:
   * <p/>
   * <pre>
   *     int i = a ? x
   *               : y;
   * </pre>
   * <p/>
   * Here <code>':'</code> is aligned to <code>'?'</code> and alignment of <code>'a'</code> is a <code>'base alignment'</code>
   * of <code>'?'</code> alignment. I.e. the thing is that <code>':'</code> is not aligned to <code>'a'</code>.
   * <p/>
   * However, we can change example as follows:
   * <p/>
   * <pre>
   *     int i = a
   *             ? x : y;
   * </pre>
   * <p/>
   * Here <code>'?'</code> is aligned to <code>'a'</code> because the later is set as a <code>'base alignment'</code> for <code>'?'</code>.
   * Note that we can't just define the same {@link #createAlignment() simple alignment} for all blocks <code>'a'</code>,
   * <code>'?'</code> and <code>':'</code> because it would produce formatting like the one below:
   * <p/>
   * <pre>
   *     int i = a ? x
   *             : y;
   * </pre>
   *
   * @param base    base alignment to use within returned alignment object
   * @return        alignment object with the given alignment defined as a <code>'base alignment'</code>
   */
  public static Alignment createChildAlignment(final Alignment base) {
    return myFactory.createChildAlignment(base);
  }
}
