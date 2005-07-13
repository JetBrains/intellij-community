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

/**
 * Defines the indent and alignment settings which are applied to a new child block
 * added to a formatting model block. Used for auto-indenting when the Enter key is pressed.
 *
 * @see Block#getChildAttributes(int)
 */

public class ChildAttributes {
  private final Indent myChildIndent;
  private final Alignment myAlignment;

  /**
   * Creates a child attributes setting with the specified indent and alignment.
   *
   * @param childIndent the indent for the child block.
   * @param alignment   the alignment for the child block.
   */
  public ChildAttributes(final Indent childIndent, final Alignment alignment) {
    myChildIndent = childIndent;
    myAlignment = alignment;
  }

  /**
   * Returns the indent of the child block.
   *
   * @return the indent setting.
   */
  public Indent getChildIndent() {
    return myChildIndent;
  }

  /**
   * Returns the alignment of the child block.
   *
   * @return the alignment setting.
   */
  public Alignment getAlignment() {
    return myAlignment;
  }
}
