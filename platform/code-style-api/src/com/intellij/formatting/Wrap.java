// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  /**
   * Creates a block wrap setting of the legacy representation of specified wrap type (see {@link WrapType#getLegacyRepresentation()}).
   *
   * @param type                the type of the wrap setting.
   * @param wrapFirstElement    determines if first block between the multiple blocks that use the same wrap object should be wrapped
   * @return                    the wrap setting instance.
   * @see #createWrap(WrapType, boolean)
   */
  public static Wrap createWrap(final int type, final boolean wrapFirstElement) {
    return Formatter.getInstance().createWrap(WrapType.byLegacyRepresentation(type), wrapFirstElement);
  }

  /**
   * Creates a block wrap setting of the specified type.
   * <p/>
   * The wrap created may be customized by the {@code 'wrap first element'} flag. It affects a situation
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
   * Consider that blocks {@code '123'} and {@code '456'} share the same wrap object. The wrap is made on the block
   * {@code '123'} if {@code 'wrap first element'} flag is {@code true}; on the block {@code '456'} otherwise
   * <p/>
   * <b>Note:</b> giving {@code 'false'} argument doesn't mean that a single block that uses that wrap can't be wrapped.
   * <p/>
   * Example:
   * <pre>
   *         |
   *   foo(12|3);
   *         |
   *         | &lt;- right margin
   * </pre>
   * Let block {@code '123'} use a wrap that was created with {@code false} as a {@code 'wrap first element'} argument.
   * The block is wrapped by the formatter then because there is no other block that uses the same wrap object and right margin is
   * exceeded.
   *
   * @param type             the type of the wrap setting.
   * @param wrapFirstElement determines if first block between the multiple blocks that use the same wrap object should be wrapped
   * @return                 the wrap setting instance.
   */
  public static Wrap createWrap(final WrapType type, final boolean wrapFirstElement) {
    return Formatter.getInstance().createWrap(type, wrapFirstElement);
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
    return Formatter.getInstance().createChildWrap(parentWrap, wrapType, wrapFirstElement);
  }
}
