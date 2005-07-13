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

import com.intellij.openapi.diagnostic.Logger;

/**
 * The indent setting for a formatting model block. Indicates how the block is indented
 * relative to its parent block.
 *
 * @see com.intellij.formatting.Block#getIndent()
 */

public abstract class Indent {
  private static IndentFactory myFactory;

  private static final Logger LOG = Logger.getInstance("#com.intellij.formatting.Indent");

  static void setFactory(IndentFactory factory) {
    LOG.assertTrue(myFactory == null);
    if (myFactory == null) {
      myFactory = factory;
    }
  }

  /**
   * Returns an instance of a regular indent, with the width specified
   * in "Project Code Style | General | Indent".
   *
   * @return the indent instance.
   */
  public static Indent getNormalIndent() {
    return myFactory.getNormalIndent();
  }

  /**
   * Returns the standard "empty indent" instance, indicating that the block is not
   * indented relative to its parent block.
   *
   * @return the empty indent instance.
   */
  public static Indent getNoneIndent() {
    return myFactory.getNoneIndent();
  }

  /**
   * Returns the "absolute none" indent instance, indicating that the block will
   * be placed at the leftmost column in the document.
   *
   * @return the indent instance.
   */
  public static Indent getAbsoluteNoneIndent() {
    return myFactory.getAbsoluteNoneIndent();
  }

  /**
   * Returns the "absolute label" indent instance, indicating that the block will be
   * indented by the number of spaces indicated in the "Project Code Style | General |
   * Label indent" setting from the leftmost column in the document.
   *
   * @return the indent instance.
   */
  public static Indent getAbsoluteLabelIndent() {
    return myFactory.getAbsoluteLabelIndent();
  }

  /**
   * Returns the "label" indent instance, indicating that the block will be indented by
   * the number of spaces indicated in the "Project Code Style | General | Label indent"
   * setting relative to its parent block.
   *
   * @return the indent instance.
   */
  public static Indent getLabelIndent() {
    return myFactory.getLabelIndent();
  }

  /**
   * Returns the "continuation" indent instance, indicating that the block will be indented by
   * the number of spaces indicated in the "Project Code Style | General | Continuation indent"
   * setting relative to its parent block.
   *
   * @return the indent instance.
   */
  public static Indent getContinuationIndent() {
    return myFactory.getContinuationIndent();
  }

  /**
   * Returns the "continuation without first" indent instance, indicating that the block will
   * be indented by the number of spaces indicated in the "Project Code Style | General | Continuation indent"
   * setting relative to its parent block, unless this block is the first of the children of its
   * parent having the same indent type. This is used for things like parameter lists, where the first parameter
   * does not have any indent and the remaining parameters are indented by the continuation indent.
   *
   * @return the indent instance.
   */
  public static Indent getContinuationWithoutFirstIndent() {//is default
    return myFactory.getContinuationWithoutFirstIndent();
  }

  /**
   * Returns an indent with the specified width.
   *
   * @param spaces the number of spaces in the indent.
   * @return the indent instance.
   */
  public static Indent getSpaceIndent(final int spaces) {
    return myFactory.getSpaceIndent(spaces);
  }
}
