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
  public ChildAttributes(@Nullable final Indent childIndent, @Nullable final Alignment alignment) {
    myChildIndent = childIndent;
    myAlignment = alignment;
  }

  /**
   * Returns the indent of the child block.
   *
   * @return the indent setting.
   */
  @Nullable
  public Indent getChildIndent() {
    return myChildIndent;
  }

  /**
   * Returns the alignment of the child block.
   *
   * @return the alignment setting.
   */
  @Nullable
  public Alignment getAlignment() {
    return myAlignment;
  }
}
