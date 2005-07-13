/*
 * Copyright (c) 2000-05 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.formatting;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Describes a single block in the {@link FormattingModel}.
 *
 * @see com.intellij.formatting.FormattingModel#getRootBlock()
 */

public interface Block {
  /**
   * Returns the text range covered by the block.
   *
   * @return the text range.
   */
  @NotNull
  TextRange getTextRange();

  /**
   * Returns the list of child blocks for the specified block.
   *
   * @return the child block list.
   * @see #isLeaf()
   */
  @NotNull
  List<Block> getSubBlocks();

  /**
   * Returns a wrap object indicating the conditions under which a line break
   * is inserted before this block when formatting, if the block extends beyond the
   * right margin.
   *
   * @return the wrap object, or null if the line break is never inserted.
   * @see Wrap#createWrap(WrapType, boolean)
   * @see Wrap#createChildWrap(Wrap, WrapType, boolean)
   */
  @Nullable
  Wrap getWrap();

  /**
   * Returns an indent object indicating how this block is indented relative
   * to its parent block.
   *
   * @return the indent object, or null if the default indent ("continuation without first") should be used.
   * @see com.intellij.formatting.Indent#getContinuationWithoutFirstIndent()
   */
  @Nullable
  Indent getIndent();

  /**
   * Returns an alignment object indicating how this block is aligned with other blocks. Blocks
   * which return the same alignment object instance from the <code>getAlignment</code> method
   * are aligned with each other.
   *
   * @return the alignment object instance, or null if no alignment is required for the block.
   */
  @Nullable
  Alignment getAlignment();

  /**
   * Returns a spacing object indicating what spaces and/or line breaks are added between two
   * specified children of this block.
   *
   * @param child1 the first child for which spacing is requested.
   * @param child2 the second child for which spacing is requested.
   * @return the spacing instance, or null if no special spacing is required. If null is returned,
   *         the formatter does not insert or delete spaces between the child blocks, but may insert
   *         a line break if the line wraps at the position between the child blocks.
   * @see Spacing#createSpacing(int, int, int, boolean, int)
   * @see Spacing#getReadOnlySpacing()
   */
  @Nullable
  Spacing getSpacing(Block child1, Block child2);

  /**
   * Returns the alignment and indent attributes which are applied to a new block inserted at
   * the specified position in the list of children of this block. Used for performing automatic
   * indent when Enter is pressed.
   *
   * @param newChildIndex the index where a new child is inserted.
   * @return the object containing the indent and alignment settings for the new child.
   */
  @NotNull
  ChildAttributes getChildAttributes(final int newChildIndex);

  /**
   * Checks if the current block is incomplete (contains elements that the user will
   * probably type but has not yet typed). For example, a parameter list is incomplete if
   * it does not have the trailing parenthesis, and a statement is incomplete if it does not
   * have the trailing semicolon. Used to determine the block for which {@link #getChildAttributes(int)}
   * is called when Enter is pressed: if the block immediately before the cursor is incomplete,
   * the method is called for that block; otherwise, the method is called for the parent of that block.
   *
   * @return true if the block is incomplete, false otherwise.
   */
  boolean isIncomplete();

  /**
   * Returns true if the specified block may not contain child blocks. Used as an optimization
   * to avoid building the complete formatting model through calls to {@link #getSubBlocks()}.
   *
   * @return true if the block is a leaf block and may not contain child blocks, false otherwise.
   */
  boolean isLeaf();
}
