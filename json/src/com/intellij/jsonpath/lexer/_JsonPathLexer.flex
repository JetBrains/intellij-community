package com.intellij.jsonpath.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.jsonpath.psi.JsonPathTypes;
import com.intellij.psi.TokenType;

import it.unimi.dsi.fastutil.ints.IntArrayList;
%%

%{
  public _JsonPathLexer() {
    this((java.io.Reader)null);
  }

  private final IntArrayList myStateStack = new IntArrayList();

  protected void resetInternal() {
    myStateStack.clear();
  }

  private void pushState(int newState) {
    myStateStack.add(yystate());
    yybegin(newState);
  }

  private void popState() {
    if (myStateStack.isEmpty()) return;

    int state = myStateStack.removeInt(myStateStack.size() - 1);
    yybegin(state);
  }
%}

%public
%class _JsonPathLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

ROOT_CONTEXT=\$
EVAL_CONTEXT=@

WHITE_SPACE=\s+
IDENTIFIER=([:letter:]|[_])([:letter:]|[_\-0-9])*

INTEGER_LITERAL=0|[1-9][0-9]*
INDEX_LITERAL=0|[\-]?[1-9][0-9]*

ESCAPE_SEQUENCE=\\[^\r\n]
SINGLE_QUOTED_STRING=\'([^\\\'\r\n]|{ESCAPE_SEQUENCE})*(\'|\\)?
DOUBLE_QUOTED_STRING=\"([^\\\"\r\n]|{ESCAPE_SEQUENCE})*(\"|\\)?

ESCAPED_SLASH=\\\/
REGEX_STRING=\/([^/]|{ESCAPED_SLASH})*(\/)?[a-zA-Z]*

DIGIT=[0-9]
DOUBLE_LITERAL=(({FLOATING_POINT_LITERAL1})|({FLOATING_POINT_LITERAL2})|({FLOATING_POINT_LITERAL3}))
FLOATING_POINT_LITERAL1=({DIGIT})+"."({DIGIT})*({EXPONENT_PART})?
FLOATING_POINT_LITERAL2="."({DIGIT})+({EXPONENT_PART})?
FLOATING_POINT_LITERAL3=({DIGIT})+({EXPONENT_PART})
EXPONENT_PART=[Ee]["+""-"]?({DIGIT})*

IN_OP=[iI][nN]
NIN_OP=[nN][iI][nN]
SUBSETOF_OP=[sS][uU][bB][sS][eE][tT][oO][fF]
ANYOF_OP=[aA][nN][yY][oO][fF]
NONEOF_OP=[nN][oO][nN][eE][oO][fF]
SIZE_OP=[sS][iI][zZ][eE]
EMPTY_OP=[eE][mM][pP][tT][yY]

%eof{
  resetInternal();
%eof}

%state WILDCARD_EXPECTED
%state REGEX_EXPECTED
%state SEGMENT_EXPRESSION
%state SCRIPT_EXPRESSION

%%

<YYINITIAL> {
  "."                                  { pushState(WILDCARD_EXPECTED); return JsonPathTypes.DOT; }
  ".."                                 { pushState(WILDCARD_EXPECTED); return JsonPathTypes.RECURSIVE_DESCENT; }
  "["                                  { pushState(SEGMENT_EXPRESSION); return JsonPathTypes.LBRACKET; }
  {ROOT_CONTEXT}                       { return JsonPathTypes.ROOT_CONTEXT; }
  {EVAL_CONTEXT}                       { return JsonPathTypes.EVAL_CONTEXT; }
  {IDENTIFIER}                         { return JsonPathTypes.IDENTIFIER; }
  {WHITE_SPACE}                        {
    if (myStateStack.isEmpty()) {
      return TokenType.BAD_CHARACTER;
    }
    popState();
    return TokenType.WHITE_SPACE;
  }
}

<WILDCARD_EXPECTED> {                  // isolated state for wildcard literals in order to not confuse "*" with MULTIPLY_OP
  "*"                                  { return JsonPathTypes.WILDCARD; }
}

<SEGMENT_EXPRESSION> {
  "*"                                  { return JsonPathTypes.WILDCARD; }
  {INDEX_LITERAL}                      { return JsonPathTypes.INTEGER_NUMBER; }
  "]"                                  { popState(); return JsonPathTypes.RBRACKET; }
}

<SCRIPT_EXPRESSION> {
  {ROOT_CONTEXT}                       { pushState(YYINITIAL); return JsonPathTypes.ROOT_CONTEXT; }
  {EVAL_CONTEXT}                       { pushState(YYINITIAL); return JsonPathTypes.EVAL_CONTEXT; }
}

<YYINITIAL, SEGMENT_EXPRESSION, SCRIPT_EXPRESSION> {
  "."                                  { return JsonPathTypes.DOT; }
  ".."                                 { return JsonPathTypes.RECURSIVE_DESCENT; }
  "["                                  { return JsonPathTypes.LBRACKET; }
  "]"                                  { return JsonPathTypes.RBRACKET; }
  "{"                                  { return JsonPathTypes.LBRACE; }
  "}"                                  { return JsonPathTypes.RBRACE; }
  "("                                  { pushState(SCRIPT_EXPRESSION); return JsonPathTypes.LPARENTH; }
  ")"                                  { popState(); return JsonPathTypes.RPARENTH; }

  "!"                                  { return JsonPathTypes.NOT_OP; }

  "=="                                 { return JsonPathTypes.EQ_OP; }
  "!="                                 { return JsonPathTypes.NE_OP; }
  "=~"                                 { pushState(REGEX_EXPECTED); return JsonPathTypes.RE_OP; }

  {IN_OP}                              { return JsonPathTypes.IN_OP; }
  {NIN_OP}                             { return JsonPathTypes.NIN_OP; }
  {SUBSETOF_OP}                        { return JsonPathTypes.SUBSETOF_OP; }
  {ANYOF_OP}                           { return JsonPathTypes.ANYOF_OP; }
  {NONEOF_OP}                          { return JsonPathTypes.NONEOF_OP; }
  {SIZE_OP}                            { return JsonPathTypes.SIZE_OP; }
  {EMPTY_OP}                           { return JsonPathTypes.EMPTY_OP; }

  ">"                                  { return JsonPathTypes.GT_OP; }
  "<"                                  { return JsonPathTypes.LT_OP; }
  ">="                                 { return JsonPathTypes.GE_OP; }
  "<="                                 { return JsonPathTypes.LE_OP; }

  "||"                                 { return JsonPathTypes.OR_OP; }
  "&&"                                 { return JsonPathTypes.AND_OP; }
  "-"                                  { return JsonPathTypes.MINUS_OP; }
  "+"                                  { return JsonPathTypes.PLUS_OP; }
  "*"                                  { return JsonPathTypes.MULTIPLY_OP; }
  "/"                                  { return JsonPathTypes.DIVIDE_OP; }

  ":"                                  { return JsonPathTypes.COLON; }
  ","                                  { return JsonPathTypes.COMMA; }
  "?"                                  { return JsonPathTypes.FILTER_OPERATOR; }
  "null"                               { return JsonPathTypes.NULL; }
  "true"                               { return JsonPathTypes.TRUE; }
  "false"                              { return JsonPathTypes.FALSE; }
  {INTEGER_LITERAL}                    { return JsonPathTypes.INTEGER_NUMBER; }
  {DOUBLE_LITERAL}                     { return JsonPathTypes.DOUBLE_NUMBER; }
  {SINGLE_QUOTED_STRING}               { return JsonPathTypes.SINGLE_QUOTED_STRING; }
  {DOUBLE_QUOTED_STRING}               { return JsonPathTypes.DOUBLE_QUOTED_STRING; }
  {WHITE_SPACE}                        { return TokenType.WHITE_SPACE; }
}

<REGEX_EXPECTED> {                     // isolated state for regex literals in order to not confuse "/" with DIVIDE_OP
  {REGEX_STRING}                       { return JsonPathTypes.REGEX_STRING; }
  {WHITE_SPACE}                        { return TokenType.WHITE_SPACE; }
}

[^] {
    if (myStateStack.isEmpty()) {
      return TokenType.BAD_CHARACTER;
    }

    yypushback(1);
    popState();
}