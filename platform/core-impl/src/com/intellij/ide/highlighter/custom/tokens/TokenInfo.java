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

package com.intellij.ide.highlighter.custom.tokens;

import com.intellij.psi.tree.IElementType;

/**
 * @author dsl
 */
public class TokenInfo {
  private int myStart;
  private int myEnd;
  private IElementType myType;

  public int getStart() {
    return myStart;
  }

  public int getEnd() {
    return myEnd;
  }

  public IElementType getType() {
    return myType;
  }

  public void updateData(int tokenStart, int tokenEnd, IElementType tokenType) {
    myStart = tokenStart;
    myEnd = tokenEnd;
    myType = tokenType;
  }

  public void updateData(TokenInfo info) {
    myStart = info.myStart;
    myEnd = info.myEnd;
    myType = info.myType;
  }
}
