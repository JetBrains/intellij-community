/*
 * Copyright (c) 2007-2009, Osmorc Development Team
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright notice, this list
 *       of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice, this
 *       list of conditions and the following disclaimer in the documentation and/or other
 *       materials provided with the distribution.
 *     * Neither the name of 'Osmorc Development Team' nor the names of its contributors may be
 *       used to endorse or promote products derived from this software without specific
 *       prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.jetbrains.lang.manifest.parser;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.lang.manifest.psi.ManifestTokenType;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public class ManifestLexer extends LexerBase {
  private static final Map<Character, IElementType> SPECIAL_CHARACTERS_TOKEN_MAPPING;
  static {
    SPECIAL_CHARACTERS_TOKEN_MAPPING = new HashMap<>();
    SPECIAL_CHARACTERS_TOKEN_MAPPING.put(':', ManifestTokenType.COLON);
    SPECIAL_CHARACTERS_TOKEN_MAPPING.put(';', ManifestTokenType.SEMICOLON);
    SPECIAL_CHARACTERS_TOKEN_MAPPING.put(',', ManifestTokenType.COMMA);
    SPECIAL_CHARACTERS_TOKEN_MAPPING.put('=', ManifestTokenType.EQUALS);
    SPECIAL_CHARACTERS_TOKEN_MAPPING.put('(', ManifestTokenType.OPENING_PARENTHESIS_TOKEN);
    SPECIAL_CHARACTERS_TOKEN_MAPPING.put(')', ManifestTokenType.CLOSING_PARENTHESIS_TOKEN);
    SPECIAL_CHARACTERS_TOKEN_MAPPING.put('[', ManifestTokenType.OPENING_BRACKET_TOKEN);
    SPECIAL_CHARACTERS_TOKEN_MAPPING.put(']', ManifestTokenType.CLOSING_BRACKET_TOKEN);
    SPECIAL_CHARACTERS_TOKEN_MAPPING.put('\"', ManifestTokenType.QUOTE);
  }

  private CharSequence myBuffer;
  private int myEndOffset;
  private int myTokenStart;
  private int myTokenEnd;
  private boolean myDefaultState;
  private IElementType myTokenType;

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myBuffer = buffer;
    myEndOffset = endOffset;
    myTokenStart = myTokenEnd = startOffset;
    myDefaultState = initialState == 0;

    parseNextToken();
  }

  @Override
  public void advance() {
    myTokenStart = myTokenEnd;
    parseNextToken();
  }

  @Override
  public int getState() {
    return myDefaultState ? 0 : 1;
  }

  @Nullable
  @Override
  public IElementType getTokenType() {
    return myTokenType;
  }

  @Override
  public int getTokenStart() {
    return myTokenStart;
  }

  @Override
  public int getTokenEnd() {
    return myTokenEnd;
  }

  @Override
  public int getBufferEnd() {
    return myEndOffset;
  }

  @NotNull
  @Override
  public CharSequence getBufferSequence() {
    return myBuffer;
  }

  private void parseNextToken() {
    if (myTokenStart >= myEndOffset) {
      myTokenType = null;
      myTokenEnd = myTokenStart;
      return;
    }

    boolean atLineStart = myTokenStart == 0 || myBuffer.charAt(myTokenStart - 1) == '\n';
    char c = myBuffer.charAt(myTokenStart);

    if (atLineStart) {
      myDefaultState = true;
      if (c == ' ') {
        myTokenType = ManifestTokenType.SIGNIFICANT_SPACE;
        myTokenEnd = myTokenStart + 1;
      }
      else if (c == '\n') {
        myTokenType = ManifestTokenType.SECTION_END;
        myTokenEnd = myTokenStart + 1;
      }
      else {
        int headerEnd = myTokenStart + 1;
        while (headerEnd < myEndOffset) {
          c = myBuffer.charAt(headerEnd);
          if (c == ':') {
            myDefaultState = false;
            break;
          }
          else if (c == '\n') {
            break;
          }
          ++headerEnd;
        }
        myTokenType = ManifestTokenType.HEADER_NAME;
        myTokenEnd = headerEnd;
      }
    }
    else if (!myDefaultState && c == ':') {
      myTokenType = ManifestTokenType.COLON;
      myTokenEnd = myTokenStart + 1;
    }
    else if (!myDefaultState && c == ' ') {
      myTokenType = ManifestTokenType.SIGNIFICANT_SPACE;
      myTokenEnd = myTokenStart + 1;
      myDefaultState = true;
    }
    else {
      myDefaultState = true;
      IElementType special;
      if (c == '\n') {
        myTokenType = ManifestTokenType.NEWLINE;
        myTokenEnd = myTokenStart + 1;
      }
      else if ((special = SPECIAL_CHARACTERS_TOKEN_MAPPING.get(c)) != null) {
        myTokenType = special;
        myTokenEnd = myTokenStart + 1;
      }
      else {
        int valueEnd = myTokenStart + 1;
        while (valueEnd < myEndOffset) {
          c = myBuffer.charAt(valueEnd);
          if (c == '\n' || SPECIAL_CHARACTERS_TOKEN_MAPPING.containsKey(c)) {
            break;
          }
          ++valueEnd;
        }
        myTokenType = ManifestTokenType.HEADER_VALUE_PART;
        myTokenEnd = valueEnd;
      }
    }
  }
}
