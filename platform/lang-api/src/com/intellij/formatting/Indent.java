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
 * The indent setting for a formatting model block. Indicates how the block is indented
 * relative to its parent block.
 *
 * @see com.intellij.formatting.Block#getIndent()
 * @see com.intellij.formatting.ChildAttributes#getChildIndent() 
 */

public abstract class Indent {
  private static IndentFactory myFactory;

  static void setFactory(IndentFactory factory) {
    myFactory = factory;
  }

  /**
   * Returns an instance of a regular indent, with the width specified
   * in "Project Code Style | General | Indent".
   * <p/>
   * <b>Note:</b> returned indent is not set to be <code>'relative'</code> to it's direct parent block
   *
   * @return the indent instance.
   */
  public static Indent getNormalIndent() {
    return myFactory.getNormalIndent(false);
  }

  //TODO den add doc
  public static Indent getNormalIndent(boolean relative) {
    return myFactory.getNormalIndent(relative);
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
   * //TODO den add doc
   *
   * @return the indent instance.
   */
  public static Indent getContinuationIndent() {
    return myFactory.getContinuationIndent(false);
  }

  //TODO den add doc
  public static Indent getContinuationIndent(boolean relative) {
    return myFactory.getContinuationIndent(relative);
  }

  /**
   * Returns the "continuation without first" indent instance, indicating that the block will
   * be indented by the number of spaces indicated in the "Project Code Style | General | Continuation indent"
   * setting relative to its parent block, unless this block is the first of the children of its
   * parent having the same indent type. This is used for things like parameter lists, where the first parameter
   * does not have any indent and the remaining parameters are indented by the continuation indent.
   * //TODO den add doc
   *
   * @return the indent instance.
   */
  public static Indent getContinuationWithoutFirstIndent() {//is default
    return myFactory.getContinuationWithoutFirstIndent(false);
  }

  //TODO den add doc
  public static Indent getContinuationWithoutFirstIndent(boolean relative) {
    return myFactory.getContinuationWithoutFirstIndent(relative);
  }

  /**
   * Returns an indent with the specified width.
   * //TODO den add doc
   *
   * @param spaces the number of spaces in the indent.
   * @return the indent instance.
   */
  public static Indent getSpaceIndent(final int spaces) {
    return myFactory.getSpaceIndent(spaces, false);
  }

  //TODO den add doc
  public static Indent getSpaceIndent(final int spaces, final boolean relative) {
    return myFactory.getSpaceIndent(spaces, relative);
  }
}
