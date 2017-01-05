package com.intellij.json;

import com.intellij.lexer.*;
import com.intellij.psi.tree.IElementType;
import static com.intellij.json.JsonElementTypes.*;

%%

%{
  public _JsonLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _JsonLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL=\R
// Edited manually, GrammarKit generates plain "\s" (no iteration)
WHITE_SPACE=\s+

LINE_COMMENT="//".*
BLOCK_COMMENT="/"\*([^*]|\*+[^*/])*(\*+"/")?
DOUBLE_QUOTED_STRING=\"([^\\\"\r\n]|\\[^\r\n])*\"?
SINGLE_QUOTED_STRING='([^\\'\r\n]|\\[^\r\n])*'?
NUMBER=-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]*)?
IDENTIFIER=[:jletter:] [:jletterdigit:]*

%%
<YYINITIAL> {
  {WHITE_SPACE}               { return com.intellij.psi.TokenType.WHITE_SPACE; }

  "{"                         { return L_CURLY; }
  "}"                         { return R_CURLY; }
  "["                         { return L_BRACKET; }
  "]"                         { return R_BRACKET; }
  ","                         { return COMMA; }
  ":"                         { return COLON; }
  "true"                      { return TRUE; }
  "false"                     { return FALSE; }
  "null"                      { return NULL; }

  {LINE_COMMENT}              { return LINE_COMMENT; }
  {BLOCK_COMMENT}             { return BLOCK_COMMENT; }
  {DOUBLE_QUOTED_STRING}      { return DOUBLE_QUOTED_STRING; }
  {SINGLE_QUOTED_STRING}      { return SINGLE_QUOTED_STRING; }
  {NUMBER}                    { return NUMBER; }
  {IDENTIFIER}                { return IDENTIFIER; }

}

[^] { return com.intellij.psi.TokenType.BAD_CHARACTER; }
