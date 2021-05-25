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

%eof{
  resetInternal();
%eof}

%state WILDCARD_EXPECTED
%state REGEX_EXPECTED
%state SEGMENT_EXPRESSION
%state SCRIPT_EXPRESSION
%state NESTED_PATH

%%

<YYINITIAL, NESTED_PATH> {
  "."                                  { pushState(WILDCARD_EXPECTED); return JsonPathTypes.DOT; }
  ".."                                 { pushState(WILDCARD_EXPECTED); return JsonPathTypes.RECURSIVE_DESCENT; }
  "["                                  { pushState(SEGMENT_EXPRESSION); return JsonPathTypes.LBRACKET; }
  {ROOT_CONTEXT}                       { return JsonPathTypes.ROOT_CONTEXT; }
  {EVAL_CONTEXT}                       { return JsonPathTypes.EVAL_CONTEXT; }
  {IDENTIFIER}                         { return JsonPathTypes.IDENTIFIER; }
  ")"                                  {
    if (myStateStack.isEmpty()) {
      return TokenType.BAD_CHARACTER;
    }
    yypushback(1);
    popState();
  }
  {WHITE_SPACE}                        {
    if (myStateStack.isEmpty()) {
      return TokenType.BAD_CHARACTER;
    }
    yypushback(1);
    popState();
  }
}

<WILDCARD_EXPECTED> {                  // isolated state for wildcard literals in order to not confuse "*" with MULTIPLY_OP
  "*"                                  { return JsonPathTypes.WILDCARD; }
}

<SEGMENT_EXPRESSION> {
  "*"                                  { return JsonPathTypes.WILDCARD; }
  {INDEX_LITERAL}                      { return JsonPathTypes.INTEGER_NUMBER; }
  "["                                  { return JsonPathTypes.LBRACKET; }
  "]"                                  { popState(); return JsonPathTypes.RBRACKET; }
}

<SCRIPT_EXPRESSION> {
  {ROOT_CONTEXT}                       { pushState(NESTED_PATH); return JsonPathTypes.ROOT_CONTEXT; }
  {EVAL_CONTEXT}                       { pushState(NESTED_PATH); return JsonPathTypes.EVAL_CONTEXT; }
  "["                                  { return JsonPathTypes.LBRACKET; }
  "]"                                  { return JsonPathTypes.RBRACKET; }
  "null"                               { return JsonPathTypes.NULL; }
  "true"                               { return JsonPathTypes.TRUE; }
  "false"                              { return JsonPathTypes.FALSE; }
  {IDENTIFIER}                         { return JsonPathTypes.NAMED_OP; }
}

<YYINITIAL, NESTED_PATH, SEGMENT_EXPRESSION, SCRIPT_EXPRESSION> {
  "."                                  { return JsonPathTypes.DOT; }
  ".."                                 { return JsonPathTypes.RECURSIVE_DESCENT; }
  "{"                                  { return JsonPathTypes.LBRACE; }
  "}"                                  { return JsonPathTypes.RBRACE; }
  "("                                  { pushState(SCRIPT_EXPRESSION); return JsonPathTypes.LPARENTH; }
  ")"                                  { popState(); return JsonPathTypes.RPARENTH; }

  "!"                                  { return JsonPathTypes.NOT_OP; }

  "==="                                { return JsonPathTypes.EEQ_OP; }
  "!=="                                { return JsonPathTypes.ENE_OP; }
  "=="                                 { return JsonPathTypes.EQ_OP; }
  "!="                                 { return JsonPathTypes.NE_OP; }
  "=~"                                 { pushState(REGEX_EXPECTED); return JsonPathTypes.RE_OP; }

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