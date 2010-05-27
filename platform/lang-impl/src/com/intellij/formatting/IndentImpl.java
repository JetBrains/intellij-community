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

import org.jetbrains.annotations.NonNls;

class IndentImpl extends Indent {
  private final boolean myIsAbsolute;
  private final boolean myRelativeToDirectParent;

  public boolean isContinuation() {
    return myType == Type.CONTINUATION_WITHOUT_FIRST;
  }

  public boolean isNone() {
    return getType() == Type.NONE;
  }

  static class Type{
    private final String myName;


    public Type(@NonNls final String name) {
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

  private final Type myType;
  private final int mySpaces;

  public IndentImpl(final Type type, boolean absolute, final int spaces, boolean relativeToDirectParent) {
    myType = type;
    myIsAbsolute = absolute;
    mySpaces = spaces;
    myRelativeToDirectParent = relativeToDirectParent;
  }

  public IndentImpl(final Type type, boolean absolute, boolean relativeToDirectParent) {
    this(type, absolute, 0, relativeToDirectParent);
  }

  Type getType() {
    return myType;
  }

  public int getSpaces() {
    return mySpaces;
  }

  /**
   * @return    <code>'isAbsolute'</code> property value as defined during {@link IndentImpl} object construction
   */
  boolean isAbsolute(){
    return myIsAbsolute;
  }

  //TODO den add doc
  public boolean isRelativeToDirectParent() {
    return myRelativeToDirectParent;
  }

  @NonNls
  @Override
  public String toString() {
    if (myType == Type.SPACES) {
      return "<Indent: SPACES(" + mySpaces + ")>";
    }
    return "<Indent: " + myType + (myIsAbsolute ? ":ABSOLUTE" : "") + ">";
  }
}
