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
package com.intellij.lexer;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;

/**
 * Used to process scriptlet code in JSP attribute values like this:
 *   attribute="<%=texts.get(\"Blabla\")%>"
 */
public class EscapedJavaLexer extends LexerBase {
  private char mySurroundingQuote;
  private final JavaLexer myJavaLexer;

  private CharSequence myBuffer;
  private int myBufferEnd;
  private int myCurOffset;
  private IElementType myTokenType = null;
  private int myTokenEnd;

  public EscapedJavaLexer(char surroundingQuote, LanguageLevel languageLevel) {
    mySurroundingQuote = surroundingQuote;
    myJavaLexer = new JavaLexer(languageLevel);
  }

  public char getSurroundingQuote() {
    return mySurroundingQuote;
  }

  public void setSurroundingQuote(char surroundingQuote) {
    mySurroundingQuote = surroundingQuote;
  }

  public void start(CharSequence buffer, int startOffset, int endOffset, int state) {
    myBuffer = buffer;
    myCurOffset = startOffset;
    myTokenEnd = startOffset;
    myBufferEnd = endOffset;
    myTokenType = null;
  }

  public CharSequence getBufferSequence() {
    return myBuffer;
  }

  public int getState() {
    return 0;
  }

  public IElementType getTokenType() {
    locateToken();
    return myTokenType;
  }

  public final int getTokenStart(){
    locateToken();
    return myCurOffset;
  }

  public final int getTokenEnd(){
    locateToken();
    return myTokenEnd;
  }

  public final void advance(){
    locateToken();
    myTokenType = null;
    myCurOffset = myTokenEnd;
  }

  public final int getBufferEnd(){
    return myBufferEnd;
  }

  private void locateToken() {
    if (myTokenType != null) return;
    if (myCurOffset >= myBufferEnd) return;

    boolean esc = false;
    int offset = myCurOffset;
    int state = 0; // 0 -- start/end
                   // 1 -- inside string
                   // 2 -- after escape (/) in string literal

    char literalStarter = 0;
    do {
      if (offset >= myBufferEnd) break;

      char c = myBuffer.charAt(offset);
      boolean wasEsc = esc;
      esc = false;
      if (c == '\\') {
        if (!wasEsc) {
          esc = true;
        }
        else {
          state = 2;
        }
      }
      else if (state == 0) {
        if (c == '\'' || c == '\"') {
          literalStarter = c;
          state = 1;
        }
      }
      else if (state == 1) {
        if (c == literalStarter) {
          state = 0;
          offset++;
          break;
        }
      }
      else if (state == 2) {
        state = 1;
      }

      if (!esc && state == 0) {
        break;
      }

      offset++;
    }
    while (true);

    if(offset >= myBufferEnd - 1) state = 0;
    switch (state){
      case 0:
        if(offset == myCurOffset){
          myJavaLexer.start(myBuffer, myCurOffset, myBufferEnd);
          myTokenType = myJavaLexer.getTokenType();
          myTokenEnd = myJavaLexer.getTokenEnd();
        }
        else {
          myTokenType = literalStarter == '\"' ? JavaTokenType.STRING_LITERAL : JavaTokenType.CHARACTER_LITERAL;
          myTokenEnd = offset;
        }
        break;

      case 1:
        myTokenType = literalStarter == '\"' ? JavaTokenType.STRING_LITERAL : JavaTokenType.CHARACTER_LITERAL;
        myTokenEnd = offset;
        break;

      default:
        myTokenType = JavaTokenType.BAD_CHARACTER;
        myTokenEnd = offset;
        break;
    }
  }
}
