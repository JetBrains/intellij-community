// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import org.jetbrains.annotations.Nullable;

/**
 * Defines the indent and alignment settings which are applied to a new child block
 * added to a formatting model block. Used for auto-indenting when the Enter key is pressed.
 *
 * @see Block#getChildAttributes(int)
 */

public class ChildAttributes {
  private final Indent myChildIndent;
  private final Alignment myAlignment;

  public static final ChildAttributes DELEGATE_TO_PREV_CHILD = new ChildAttributes(null, null);
  public static final ChildAttributes DELEGATE_TO_NEXT_CHILD = new ChildAttributes(null, null);

  /**
   * Creates a child attributes setting with the specified indent and alignment.
   *
   * @param childIndent the indent for the child block.
   * @param alignment   the alignment for the child block.
   */
  public ChildAttributes(final @Nullable Indent childIndent, final @Nullable Alignment alignment) {
    myChildIndent = childIndent;
    myAlignment = alignment;
  }

  /**
   * Returns the indent of the child block.
   *
   * @return the indent setting.
   */
  public @Nullable Indent getChildIndent() {
    return myChildIndent;
  }

  /**
   * Returns the alignment of the child block.
   *
   * @return the alignment setting.
   */
  public @Nullable Alignment getAlignment() {
    return myAlignment;
  }
}
