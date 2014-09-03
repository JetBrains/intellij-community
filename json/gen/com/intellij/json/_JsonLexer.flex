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

EOL="\r"|"\n"|"\r\n"
LINE_WS=[\ \t\f]
WHITE_SPACE=({LINE_WS}|{EOL})+

LINE_COMMENT="//".*
BLOCK_COMMENT="/"\*([^*]|\*+[^*/])*(\*+"/")?
STRING=\"([^\\\"\r\n]|\\[^\r\n])*\"?|'([^\\\"\r\n]|\\[^\r\n])*'?
NUMBER=-?[0-9]+(\.[0-9]+([eE][+-]?[0-9]+)?)?
TEXT=[a-zA-Z_0-9]+

%%
<YYINITIAL> {
  {WHITE_SPACE}        { return com.intellij.psi.TokenType.WHITE_SPACE; }

  "{"                  { return L_CURLY; }
  "}"                  { return R_CURLY; }
  "["                  { return L_BRACKET; }
  "]"                  { return R_BRACKET; }
  ","                  { return COMMA; }
  ":"                  { return COLON; }
  "true"               { return TRUE; }
  "false"              { return FALSE; }
  "null"               { return NULL; }

  {LINE_COMMENT}       { return LINE_COMMENT; }
  {BLOCK_COMMENT}      { return BLOCK_COMMENT; }
  {STRING}             { return STRING; }
  {NUMBER}             { return NUMBER; }
  {TEXT}               { return TEXT; }

  [^] { return com.intellij.psi.TokenType.BAD_CHARACTER; }
}
