/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
 * The wrap setting for a formatting model block. Indicates the conditions under which a line break
 * is inserted before the block when formatting, if the block extends beyond the
 * right margin.
 *
 * @see Block#getWrap()
 */
public abstract class Wrap {
  /**
   * Converts a low-priority wrap setting to a regular wrap setting.
   * @see #createChildWrap(Wrap, WrapType, boolean)
   */
  public abstract void ignoreParentWraps();

  private static WrapFactory myFactory;

  static void setFactory(WrapFactory factory) {
    myFactory = factory;
  }

  /**
   * Creates a block wrap setting of the legacy representation of specified wrap type (see {@link WrapType#getLegacyRepresentation()}).
   *
   * @param type                the type of the wrap setting.
   * @param wrapFirstElement    determines if first block between the multiple blocks that use the same wrap object should be wrapped
   * @return                    the wrap setting instance.
   * @see #createWrap(WrapType, boolean)
   */
  public static Wrap createWrap(final int type, final boolean wrapFirstElement) {
    return myFactory.createWrap(WrapType.byLegacyRepresentation(type), wrapFirstElement);
  }

  /**
   * Creates a block wrap setting of the specified type.
   * <p/>
   * The wrap created may be customized by the <code>'wrap first element'</code> flag. It affects a situation
   * when there are multiple blocks that share the same wrap object. It determines if the first block
   * should be wrapped when subsequent blocks exceeds right margin.
   * <p/>
   * Example:
   * <pre>
   *             |
   *   foo(123, 4|56
   *             |
   *             | &lt;- right margin
   * </pre>
   * Consider that blocks <code>'123'</code> and <code>'456'</code> share the same wrap object. The wrap is made on the block
   * <code>'123'</code> if <code>'wrap first element'</code> flag is <code>true</code>; on the block <code>'456'</code> otherwise
   * <p/>
   * <b>Note:</b> giving <code>'false'</code> argument doesn't mean that a single block that uses that wrap can't be wrapped.
   * <p/>
   * Example:
   * <pre>
   *         |
   *   foo(12|3);
   *         |
   *         | &lt;- right margin
   * </pre>
   * Let block <code>'123'</code> use a wrap that was created with <code>false</code> as a <code>'wrap first element'</code> argument.
   * The block is wrapped by the formatter then because there is no other block that uses the same wrap object and right margin is
   * exceeded.
   *
   * @param type             the type of the wrap setting.
   * @param wrapFirstElement determines if first block between the multiple blocks that use the same wrap object should be wrapped
   * @return                 the wrap setting instance.
   */
  public static Wrap createWrap(final WrapType type, final boolean wrapFirstElement) {
    return myFactory.createWrap(type, wrapFirstElement);
  }

  /**
   * Creates a low priority wrap setting of the specified type. If the formatter detects that
   * the line should be wrapped at a location of the child wrap, the line is wrapped at the
   * position of its parent wrap instead.
   *
   * @param parentWrap       the parent for this wrap setting.
   * @param wrapType         the type of the wrap setting.
   * @param wrapFirstElement if true, the first element in a sequence of elements of the same type
   *                         is also wrapped.
   * @return the wrap setting instance.
   * @see #ignoreParentWraps()
   */
  public static Wrap createChildWrap(final Wrap parentWrap, final WrapType wrapType, final boolean wrapFirstElement) {
    return myFactory.createChildWrap(parentWrap, wrapType, wrapFirstElement);
  }
}
