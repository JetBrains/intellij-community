package com.intellij.json.syntax

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.util.lexer.FlexLexer

import com.intellij.platform.syntax.element.SyntaxTokenTypes.WHITE_SPACE
import com.intellij.platform.syntax.element.SyntaxTokenTypes.BAD_CHARACTER
import com.intellij.json.syntax.JsonSyntaxElementTypes
import kotlin.jvm.JvmStatic

%%

%public
%class _JsonLexer
%implements FlexLexer
%function advance
%type SyntaxElementType
%unicode

EOL=\R
WHITE_SPACE=\s+

LINE_COMMENT="//".*
BLOCK_COMMENT="/"\*([^*]|\*+[^*/])*(\*+"/")?
DOUBLE_QUOTED_STRING=\"([^\\\"\r\n]|\\[^\r\n])*\"?
SINGLE_QUOTED_STRING='([^\\'\r\n]|\\[^\r\n])*'?
NUMBER=(-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]*)?)|Infinity|-Infinity|NaN
IDENTIFIER=[[:jletterdigit:]~!()*\-."/"@\^<>=]+

%%
<YYINITIAL> {
  {WHITE_SPACE}               { return WHITE_SPACE; }

  "{"                         { return JsonSyntaxElementTypes.L_CURLY; }
  "}"                         { return JsonSyntaxElementTypes.R_CURLY; }
  "["                         { return JsonSyntaxElementTypes.L_BRACKET; }
  "]"                         { return JsonSyntaxElementTypes.R_BRACKET; }
  ","                         { return JsonSyntaxElementTypes.COMMA; }
  ":"                         { return JsonSyntaxElementTypes.COLON; }
  "true"                      { return JsonSyntaxElementTypes.TRUE; }
  "false"                     { return JsonSyntaxElementTypes.FALSE; }
  "null"                      { return JsonSyntaxElementTypes.NULL; }

  {LINE_COMMENT}              { return JsonSyntaxElementTypes.LINE_COMMENT; }
  {BLOCK_COMMENT}             { return JsonSyntaxElementTypes.BLOCK_COMMENT; }
  {DOUBLE_QUOTED_STRING}      { return JsonSyntaxElementTypes.DOUBLE_QUOTED_STRING; }
  {SINGLE_QUOTED_STRING}      { return JsonSyntaxElementTypes.SINGLE_QUOTED_STRING; }
  {NUMBER}                    { return JsonSyntaxElementTypes.NUMBER; }
  {IDENTIFIER}                { return JsonSyntaxElementTypes.IDENTIFIER; }

}

[^] { return BAD_CHARACTER; }
