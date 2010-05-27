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
 * <p/>
 * Number of factory methods of this class use <code>'indent relative to direct parent'</code> flag. It specified anchor parent block
 * to use to apply indent.
 * <p/>
 * Consider the following situation:
 * <p/>
 * <pre>
 *     return a == 0
                  && (b == 0
                          || c == 0);
 * </pre>
 * <p/>
 * Here is the following blocks hierarchy (going from child to parent):
 * <p/>
 * <ul>
 *   <li><code>'|| c == 0`</code>;</li>
 *   <li><code>'b == 0 || c == 0'</code>;</li>
 *   <li><code>'(b == 0 || c == 0)'</code>;</li>
 *   <li><code>'a == 0 && (b == 0 || c == 0)'</code>;</li>
 *   <li><code>'return a == 0 && (b == 0 || c == 0)'</code>;</li>
 * </ul>
 * <p/>
 * By default formatter applies block indent to the first block ancestor (direct or indirect) that starts on a new line. That means
 * that such an ancestor for both blocks <code>'|| c == 0'</code> and <code>'&& (b == 0 || c == 0)'</code>
 * is <code>'return a == 0 && (b == 0 || c == 0)'</code>. That means that the code above is formatted as follows:
 * <p/>
 * <pre>
 *    return a == 0
 *        && (b == 0
 *        || c == 0);
 * </pre>
 * <p/>
 * In contrast, it's possible to specify that direct parent block that starts on a line before target child block is used as an anchor.
 * Initial formatting example illustrates such approach.
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
   * @see #getNormalIndent(boolean)
   */
  public static Indent getNormalIndent() {
    return myFactory.getNormalIndent(false);
  }

  /**
   * Returns an instance of a regular indent, with the width specified
   * in "Project Code Style | General | Indent" and given <code>'relative to direct parent'</code> flag
   *
   * @param relativeToDirectParent    flag the indicates if current indent object anchors direct block parent (feel free
   *                                  to get more information about that at class-level javadoc)
   * @return                          newly created indent instance configured in accordance with the given parameter
   */
  public static Indent getNormalIndent(boolean relativeToDirectParent) {
    return myFactory.getNormalIndent(relativeToDirectParent);
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
   * <p/>
   * <b>Note:</b> returned indent is not set to be <code>'relative'</code> to it's direct parent block
   *
   * @return the indent instance.
   * @see #getContinuationIndent(boolean)
   */
  public static Indent getContinuationIndent() {
    return myFactory.getContinuationIndent(false);
  }

  /**
   * Returns the "continuation" indent instance, indicating that the block will be indented by
   * the number of spaces indicated in the "Project Code Style | General | Continuation indent"
   * setting relative to its parent block  and given <code>'relative to direct parent'</code> flag.
   *
   * @param relativeToDirectParent    flag the indicates if current indent object anchors direct block parent (feel free
   *                                  to get more information about that at class-level javadoc)
   * @return                          newly created indent instance configured in accordance with the given parameter
   */
  public static Indent getContinuationIndent(boolean relativeToDirectParent) {
    return myFactory.getContinuationIndent(relativeToDirectParent);
  }

  /**
   * Returns the "continuation without first" indent instance, indicating that the block will
   * be indented by the number of spaces indicated in the "Project Code Style | General | Continuation indent"
   * setting relative to its parent block, unless this block is the first of the children of its
   * parent having the same indent type. This is used for things like parameter lists, where the first parameter
   * does not have any indent and the remaining parameters are indented by the continuation indent.
   * <p/>
   * <b>Note:</b> returned indent is not set to be <code>'relative'</code> to it's direct parent block
   *
   * @return the indent instance.
   * @see #getContinuationWithoutFirstIndent(boolean)
   */
  public static Indent getContinuationWithoutFirstIndent() {//is default
    return myFactory.getContinuationWithoutFirstIndent(false);
  }

  /**
   * Returns the "continuation without first" indent instance, indicating that the block will
   * be indented by the number of spaces indicated in the "Project Code Style | General | Continuation indent"
   * setting relative to its parent block, unless this block is the first of the children of its
   * parent having the same indent type. This is used for things like parameter lists, where the first parameter
   * does not have any indent and the remaining parameters are indented by the continuation indent  and given
   * <code>'relative to direct parent'</code> flag.
   *
   * @param relativeToDirectParent    flag the indicates if current indent object anchors direct block parent (feel free
   *                                  to get more information about that at class-level javadoc)
   * @return                          newly created indent instance configured in accordance with the given parameter
   */
  public static Indent getContinuationWithoutFirstIndent(boolean relativeToDirectParent) {
    return myFactory.getContinuationWithoutFirstIndent(relativeToDirectParent);
  }

  /**
   * Returns an indent with the specified width.
   * <p/>
   * <b>Note:</b> returned indent is not set to be <code>'relative'</code> to it's direct parent block
   *
   * @param spaces the number of spaces in the indent.
   * @return the indent instance.
   * @see #getSpaceIndent(int, boolean)
   */
  public static Indent getSpaceIndent(final int spaces) {
    return myFactory.getSpaceIndent(spaces, false);
  }

  /**
   * Returns an indent with the specified width  and given <code>'relative to direct parent'</code> flag.
   *
   * @param spaces                    the number of spaces in the indent
   * @param relativeToDirectParent    flag the indicates if current indent object anchors direct block parent (feel free
   *                                  to get more information about that at class-level javadoc)
   * @return                          newly created indent instance configured in accordance with the given parameter
   */
  public static Indent getSpaceIndent(final int spaces, final boolean relativeToDirectParent) {
    return myFactory.getSpaceIndent(spaces, relativeToDirectParent);
  }
}
