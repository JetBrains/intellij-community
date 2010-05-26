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

public class IndentData {
  private int myIndentSpaces = 0;
  private int mySpaces = 0;

  public IndentData(final int indentSpaces, final int spaces) {
    myIndentSpaces = indentSpaces;
    mySpaces = spaces;
  }

  public IndentData(final int indentSpaces) {
    this(indentSpaces, 0);
  }

  public int getTotalSpaces() {
    return mySpaces + myIndentSpaces;
  }

  public int getIndentSpaces() {
    return myIndentSpaces;
  }

  public int getSpaces() {
    return mySpaces;
  }

  public IndentData add(final IndentData childOffset) {
    return new IndentData(myIndentSpaces + childOffset.getIndentSpaces(), mySpaces + childOffset.getSpaces());
  }

  public IndentData add(final WhiteSpace whiteSpace) {
    return new IndentData(myIndentSpaces + whiteSpace.getIndentOffset(), mySpaces + whiteSpace.getSpaces());
  }

  public boolean isEmpty() {
    return myIndentSpaces == 0 && mySpaces == 0;
  }

  public IndentInfo createIndentInfo() {
    return new IndentInfo(0, myIndentSpaces, mySpaces);
  }

  @Override
  public String toString() {
    return "spaces=" + mySpaces + ", indent spaces=" + myIndentSpaces;
  }
}
