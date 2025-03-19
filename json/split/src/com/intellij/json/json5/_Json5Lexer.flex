package com.intellij.json.json5;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static com.intellij.json.JsonElementTypes.*;

%%

%{
  public _Json5Lexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _Json5Lexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL=\R
WHITE_SPACE=\s+
HEX_DIGIT=[0-9A-Fa-f]

LINE_COMMENT="//".*
BLOCK_COMMENT="/"\*([^*]|\*+[^*/])*(\*+"/")?
LINE_TERMINATOR_SEQUENCE=\R
CRLF= [\ \t \f]* {LINE_TERMINATOR_SEQUENCE}
DOUBLE_QUOTED_STRING=\"([^\\\"\r\n]|\\[^\r\n]|\\{CRLF})*\"?
SINGLE_QUOTED_STRING='([^\\'\r\n]|\\[^\r\n]|\\{CRLF})*'?
JSON5_NUMBER=(\+|-)?(0|[1-9][0-9]*)?\.?([0-9]+)?([eE][+-]?[0-9]*)?
HEX_DIGITS=({HEX_DIGIT})+
HEX_INTEGER_LITERAL=(\+|-)?0[Xx]({HEX_DIGITS})
NUMBER={JSON5_NUMBER}|{HEX_INTEGER_LITERAL}|Infinity|-Infinity|\+Infinity|NaN|-NaN|\+NaN
IDENTIFIER=[[:jletterdigit:]~!()*\-."/"@\^<>=]+

%%
<YYINITIAL> {
  {WHITE_SPACE}               { return WHITE_SPACE; }

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

[^] { return BAD_CHARACTER; }
