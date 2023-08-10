// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * The indent setting for a formatting model block. Indicates how the block is indented
 * relative to its parent block.
 * <p/>
 * <b>Relative indents</b>
 * <p/>
 * Number of factory methods of this class use {@code 'indent relative to direct parent'} flag. It specified anchor parent block
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
 *   <li>{@code '|| c == 0`};</li>
 *   <li>{@code 'b == 0 || c == 0'};</li>
 *   <li>{@code '(b == 0 || c == 0)'};</li>
 *   <li>{@code 'a == 0 && (b == 0 || c == 0)'};</li>
 *   <li>{@code 'return a == 0 && (b == 0 || c == 0)'};</li>
 * </ul>
 * <p/>
 * By default formatter applies block indent to the first block ancestor (direct or indirect) that starts on a new line. That means
 * that such an ancestor for both blocks {@code '|| c == 0'} and {@code '&& (b == 0 || c == 0)'}
 * is {@code 'return a == 0 && (b == 0 || c == 0)'}. That means that the code above is formatted as follows:
 * <p/>
 * <pre>
 *    return a == 0
 *        && (b == 0
 *        || c == 0);
 * </pre>
 * <p/>
 * In contrast, it's possible to specify that direct parent block that starts on a line before target child block is used as an anchor.
 * Initial formatting example illustrates such approach.
 * <p/>
 * <b>Enforcing indent to children</b>
 * <p/>
 * It's possible to configure indent to enforce parent block indent to its children that start new line. Consider the following situation:
 * <pre>
 *   foo("test", new Runnable() {
 *           public void run() {
 *           }
 *       },
 *       new Runnable() {
 *           public void run() {
 *           }
 *       }
 *   );
 * </pre>
 * We want the first {@code 'new Runnable() {...}'} block here to be indented to the method expression list element. However, formatter
 * uses indents only if the block starts new line. Here the block doesn't start new line ({@code 'new Runnable() ...'}), hence
 * we need to define {@code 'enforce indent to children'} flag in order to instruct formatter to apply parent indent to the sub-blocks.
 *
 * @see Block#getIndent()
 * @see ChildAttributes#getChildIndent()
 */

public abstract class Indent {
  @NotNull
  public abstract Type getType();

  /**
   * Returns an instance of a regular indent, with the width specified
   * in "Project Code Style | General | Indent".
   * <p/>
   * <b>Note:</b> returned indent is not set to be {@code 'relative'} to it's direct parent block
   *
   * @return the indent instance.
   * @see #getNormalIndent(boolean)
   */
  public static Indent getNormalIndent() {
    return Formatter.getInstance().getNormalIndent(false);
  }

  /**
   * Returns an instance of a regular indent, with the width specified
   * in "Project Code Style | General | Indent" and given {@code 'relative to direct parent'} flag
   *
   * @param relativeToDirectParent    flag the indicates if current indent object anchors direct block parent (feel free
   *                                  to get more information about that at class-level javadoc)
   * @return                          newly created indent instance configured in accordance with the given parameter
   */
  public static Indent getNormalIndent(boolean relativeToDirectParent) {
    return Formatter.getInstance().getNormalIndent(relativeToDirectParent);
  }

  /**
   * Returns the standard "empty indent" instance, indicating that the block is not
   * indented relative to its parent block.
   *
   * @return the empty indent instance.
   */
  public static Indent getNoneIndent() {
    return Formatter.getInstance().getNoneIndent();
  }

  /**
   * Returns the "absolute none" indent instance, indicating that the block will
   * be placed at the leftmost column in the document.
   *
   * @return the indent instance.
   */
  public static Indent getAbsoluteNoneIndent() {
    return Formatter.getInstance().getAbsoluteNoneIndent();
  }

  /**
   * Returns the "absolute label" indent instance, indicating that the block will be
   * indented by the number of spaces indicated in the "Project Code Style | General |
   * Label indent" setting from the leftmost column in the document.
   *
   * @return the indent instance.
   */
  public static Indent getAbsoluteLabelIndent() {
    return Formatter.getInstance().getAbsoluteLabelIndent();
  }

  /**
   * Returns the "label" indent instance, indicating that the block will be indented by
   * the number of spaces indicated in the "Project Code Style | General | Label indent"
   * setting relative to its parent block.
   *
   * @return the indent instance.
   */
  public static Indent getLabelIndent() {
    return Formatter.getInstance().getLabelIndent();
  }

  /**
   * Returns the "continuation" indent instance, indicating that the block will be indented by
   * the number of spaces indicated in the "Project Code Style | General | Continuation indent"
   * setting relative to its parent block.
   * <p/>
   * <b>Note:</b> returned indent is not set to be {@code 'relative'} to it's direct parent block
   *
   * @return the indent instance.
   * @see #getContinuationIndent(boolean)
   */
  public static Indent getContinuationIndent() {
    return Formatter.getInstance().getContinuationIndent(false);
  }

  /**
   * Returns the "continuation" indent instance, indicating that the block will be indented by
   * the number of spaces indicated in the "Project Code Style | General | Continuation indent"
   * setting relative to its parent block  and given {@code 'relative to direct parent'} flag.
   *
   * @param relativeToDirectParent    flag the indicates if current indent object anchors direct block parent (feel free
   *                                  to get more information about that at class-level javadoc)
   * @return                          newly created indent instance configured in accordance with the given parameter
   */
  public static Indent getContinuationIndent(boolean relativeToDirectParent) {
    return Formatter.getInstance().getContinuationIndent(relativeToDirectParent);
  }

  /**
   * Returns the "continuation without first" indent instance, indicating that the block will
   * be indented by the number of spaces indicated in the "Project Code Style | General | Continuation indent"
   * setting relative to its parent block, unless this block is the first of the children of its
   * parent having the same indent type. This is used for things like parameter lists, where the first parameter
   * does not have any indent and the remaining parameters are indented by the continuation indent.
   * <p/>
   * <b>Note:</b> returned indent is not set to be {@code 'relative'} to it's direct parent block
   *
   * @return the indent instance.
   * @see #getContinuationWithoutFirstIndent(boolean)
   */
  public static Indent getContinuationWithoutFirstIndent() {//is default
    return Formatter.getInstance().getContinuationWithoutFirstIndent(false);
  }

  /**
   * Returns the "continuation without first" indent instance, indicating that the block will
   * be indented by the number of spaces indicated in the "Project Code Style | General | Continuation indent"
   * setting relative to its parent block, unless this block is the first of the children of its
   * parent having the same indent type. This is used for things like parameter lists, where the first parameter
   * does not have any indent and the remaining parameters are indented by the continuation indent  and given
   * {@code 'relative to direct parent'} flag.
   *
   * @param relativeToDirectParent    flag the indicates if current indent object anchors direct block parent (feel free
   *                                  to get more information about that at class-level javadoc)
   * @return                          newly created indent instance configured in accordance with the given parameter
   */
  public static Indent getContinuationWithoutFirstIndent(boolean relativeToDirectParent) {
    return Formatter.getInstance().getContinuationWithoutFirstIndent(relativeToDirectParent);
  }

  /**
   * Returns an indent with the specified width.
   * <p/>
   * <b>Note:</b> returned indent is not set to be {@code 'relative'} to it's direct parent block
   *
   * @param spaces the number of spaces in the indent.
   * @return the indent instance.
   * @see #getSpaceIndent(int, boolean)
   */
  public static Indent getSpaceIndent(final int spaces) {
    return Formatter.getInstance().getSpaceIndent(spaces, false);
  }

  /**
   * Returns an indent with the specified width  and given {@code 'relative to direct parent'} flag.
   *
   * @param spaces                    the number of spaces in the indent
   * @param relativeToDirectParent    flag the indicates if current indent object anchors direct block parent (feel free
   *                                  to get more information about that at class-level javadoc)
   * @return                          newly created indent instance configured in accordance with the given parameter
   */
  public static Indent getSpaceIndent(final int spaces, final boolean relativeToDirectParent) {
    return Formatter.getInstance().getSpaceIndent(spaces, relativeToDirectParent);
  }

  /**
   * Base factory method for {@link Indent} objects construction, i.e. all other methods may be expressed in terms of this method.
   *
   * @param type                      indent type
   * @param relativeToDirectParent    flag the indicates if current indent object anchors direct block parent (feel free
   *                                  to get more information about that at class-level javadoc)
   * @param enforceIndentToChildren   flag the indicates if current indent object should be enforced for multiline block children
   *                                  (feel free to get more information about that at class-level javadoc)
   * @return                          newly created indent configured in accordance with the given arguments
   */
  public static Indent getIndent(@NotNull Type type, boolean relativeToDirectParent, boolean enforceIndentToChildren) {
    return Formatter.getInstance().getIndent(type, relativeToDirectParent, enforceIndentToChildren);
  }

  /**
   * Base factory method for {@link Indent} objects construction, i.e. all other methods may be expressed in terms of this method.
   *
   * @param type                      indent type
   * @param spaces                    the number of spaces in the indent
   * @param relativeToDirectParent    flag the indicates if current indent object anchors direct block parent (feel free
   *                                  to get more information about that at class-level javadoc)
   * @param enforceIndentToChildren   flag the indicates if current indent object should be enforced for multiline block children
   *                                  (feel free to get more information about that at class-level javadoc)
   * @return                          newly created indent configured in accordance with the given arguments
   */
  public static Indent getIndent(@NotNull Type type, int spaces, boolean relativeToDirectParent, boolean enforceIndentToChildren) {
    return Formatter.getInstance().getIndent(type, spaces, relativeToDirectParent, enforceIndentToChildren);
  }

  public static Indent getSmartIndent(Type type) {
    return Formatter.getInstance().getSmartIndent(type);
  }

  public static Indent getSmartIndent(Type type, boolean relativeToDirectParent) {
    return Formatter.getInstance().getSmartIndent(type, relativeToDirectParent);
  }

  public static final class Type {
    private final String myName;


    private Type(@NonNls final String name) {
      myName = name;
    }

    public static final Type SPACES = new Type("SPACES");
    public static final Type NONE = new Type("NONE");
    public static final Type LABEL = new Type("LABEL");
    public static final Type NORMAL = new Type("NORMAL");
    public static final Type CONTINUATION = new Type("CONTINUATION");
    public static final Type CONTINUATION_WITHOUT_FIRST = new Type("CONTINUATION_WITHOUT_FIRST");

    public String toString() {
      return myName;
    }
  }
}
