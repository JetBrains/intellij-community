/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.spi.parsing;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.spi.parsing.SPITokenType;
import com.intellij.lexer.FlexLexer;

@SuppressWarnings({"ALL"})
%%

%{
  public _SPILexer() {
    this((java.io.Reader)null);
  }

  public void goTo(int offset) {
    zzCurrentPos = zzMarkedPos = zzStartRead = offset;
    zzPushbackPos = 0;
    zzAtEOF = offset < zzEndRead;
  }
%}

%unicode
%class _SPILexer
%implements FlexLexer
%function advance
%type IElementType
%eof{  return;
%eof}

WHITE_SPACE_CHAR=[\ \n\r\t\f]

IDENTIFIER=[:jletter:] [:jletterdigit:]*

END_OF_LINE_COMMENT="#"[^\r\n]*

DIGIT = [0-9]
DIGIT_OR_UNDERSCORE = [_0-9]
DIGITS = {DIGIT} | {DIGIT} {DIGIT_OR_UNDERSCORE}*

%%

<YYINITIAL> {WHITE_SPACE_CHAR}+ { return JavaTokenType.WHITE_SPACE; }

<YYINITIAL> {END_OF_LINE_COMMENT} { return JavaTokenType.END_OF_LINE_COMMENT; }
<YYINITIAL> {IDENTIFIER} { return JavaTokenType.IDENTIFIER; }

<YYINITIAL> "."   { return JavaTokenType.DOT; }

<YYINITIAL> "$" { return SPITokenType.DOLLAR; }

<YYINITIAL> . { return JavaTokenType.BAD_CHARACTER; }
