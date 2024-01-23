// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.formatting;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class IndentImpl extends Indent {
  private final boolean myIsAbsolute;
  private final boolean myRelativeToDirectParent;
  private final boolean myEnforceChildrenToBeRelativeToMe;

  private final @NotNull Type myType;
  private final int mySpaces;
  private final boolean myEnforceIndentToChildren;

  public IndentImpl(@NotNull Type type, boolean absolute, boolean relativeToDirectParent) {
    this(type, absolute, 0, relativeToDirectParent, false);
  }

  public IndentImpl(@NotNull Type type, boolean absolute, final int spaces, boolean relativeToDirectParent, boolean enforceIndentToChildren) {
    this(type, absolute, spaces, relativeToDirectParent, enforceIndentToChildren, false);
  }

  public IndentImpl(@NotNull Type type, boolean absolute, final int spaces, boolean relativeToDirectParent, boolean enforceIndentToChildren, boolean enforceChildrenToBeRelativeToMe) {
    myType = type;
    myIsAbsolute = absolute;
    mySpaces = spaces;
    myRelativeToDirectParent = relativeToDirectParent;
    myEnforceIndentToChildren = enforceIndentToChildren;
    myEnforceChildrenToBeRelativeToMe = enforceChildrenToBeRelativeToMe;
    if (myEnforceChildrenToBeRelativeToMe) {
      assert myEnforceIndentToChildren;
      assert !myRelativeToDirectParent;
      assert !myIsAbsolute;
    }
  }

  @Override
  public @NotNull Type getType() {
    return myType;
  }

  public int getSpaces() {
    return mySpaces;
  }

  /**
   * @return    {@code 'isAbsolute'} property value as defined during {@link IndentImpl} object construction
   */
  public boolean isAbsolute() {
    return myIsAbsolute;
  }

  /**
   * Allows to answer if current indent object is configured to anchor direct parent that lays on a different line.
   * <p/>
   * Feel free to check {@link Indent} class-level javadoc in order to get more information and examples about expected
   * usage of this property.
   *
   * @return      flag that indicates if this indent should anchor direct parent that lays on a different line
   */
  public boolean isRelativeToDirectParent() {
    return myRelativeToDirectParent;
  }

  @ApiStatus.Experimental
  public boolean isEnforceChildrenToBeRelativeToMe() {
    return myEnforceChildrenToBeRelativeToMe;
  }
  /**
   * Allows to answer if current indent object is configured to enforce indent for sub-blocks of composite block that doesn't start
   * new line.
   * <p/>
   * Feel free to check {@link Indent} javadoc for the more detailed explanation of this property usage.
   * 
   * @return      {@code true} if current indent object is configured to enforce indent for sub-blocks of composite block
   *              that doesn't start new line; {@code false} otherwise
   */
  public boolean isEnforceIndentToChildren() {
    return myEnforceIndentToChildren;
  }

  @Override
  public @NonNls String toString() {
    if (myType == Type.SPACES) {
      return "<Indent: SPACES(" + mySpaces + ")>";
    }
    return "<Indent: " + myType + (myIsAbsolute ? ":ABSOLUTE " : "")
           + (myRelativeToDirectParent ? " relative to direct parent " : "")
           + (myEnforceChildrenToBeRelativeToMe ? " enforce children to be relative to me" :
              myEnforceIndentToChildren ? " enforce indent to children" : "") + ">";
  }
}
