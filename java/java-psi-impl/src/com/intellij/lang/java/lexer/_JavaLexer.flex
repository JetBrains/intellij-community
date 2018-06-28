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
package com.intellij.lang.java.lexer;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.lexer.FlexLexer;

@SuppressWarnings({"ALL"})
%%

%{
  private boolean myAssertKeyword;
  private boolean myEnumKeyword;

  public _JavaLexer(LanguageLevel level) {
    this((java.io.Reader)null);
    myAssertKeyword = level.isAtLeast(LanguageLevel.JDK_1_4);
    myEnumKeyword = level.isAtLeast(LanguageLevel.JDK_1_5);
  }

  public void goTo(int offset) {
    zzCurrentPos = zzMarkedPos = zzStartRead = offset;
    zzAtEOF = false;
  }
%}

%unicode
%class _JavaLexer
%implements FlexLexer
%function advance
%type IElementType

WHITE_SPACE_CHAR = [\ \n\r\t\f]

IDENTIFIER = [:jletter:] [:jletterdigit:]*

C_STYLE_COMMENT=("/*"[^"*"]{COMMENT_TAIL})|"/*"
DOC_COMMENT="/*""*"+("/"|([^"/""*"]{COMMENT_TAIL}))?
COMMENT_TAIL=([^"*"]*("*"+[^"*""/"])?)*("*"+"/")?
END_OF_LINE_COMMENT="/""/"[^\r\n]*

DIGIT = [0-9]
DIGIT_OR_UNDERSCORE = [_0-9]
DIGITS = {DIGIT} | {DIGIT} {DIGIT_OR_UNDERSCORE}*
HEX_DIGIT_OR_UNDERSCORE = [_0-9A-Fa-f]

INTEGER_LITERAL = {DIGITS} | {HEX_INTEGER_LITERAL} | {BIN_INTEGER_LITERAL}
LONG_LITERAL = {INTEGER_LITERAL} [Ll]
HEX_INTEGER_LITERAL = 0 [Xx] {HEX_DIGIT_OR_UNDERSCORE}*
BIN_INTEGER_LITERAL = 0 [Bb] {DIGIT_OR_UNDERSCORE}*

FLOAT_LITERAL = ({DEC_FP_LITERAL} | {HEX_FP_LITERAL}) [Ff] | {DIGITS} [Ff]
DOUBLE_LITERAL = ({DEC_FP_LITERAL} | {HEX_FP_LITERAL}) [Dd]? | {DIGITS} [Dd]
DEC_FP_LITERAL = {DIGITS} {DEC_EXPONENT} | {DEC_SIGNIFICAND} {DEC_EXPONENT}?
DEC_SIGNIFICAND = "." {DIGITS} | {DIGITS} "." {DIGIT_OR_UNDERSCORE}*
DEC_EXPONENT = [Ee] [+-]? {DIGIT_OR_UNDERSCORE}*
HEX_FP_LITERAL = {HEX_SIGNIFICAND} {HEX_EXPONENT}
HEX_SIGNIFICAND = 0 [Xx] ({HEX_DIGIT_OR_UNDERSCORE}+ "."? | {HEX_DIGIT_OR_UNDERSCORE}* "." {HEX_DIGIT_OR_UNDERSCORE}+)
HEX_EXPONENT = [Pp] [+-]? {DIGIT_OR_UNDERSCORE}*

ESCAPE_SEQUENCE = \\[^\r\n]
CHARACTER_LITERAL = "'" ([^\\\'\r\n] | {ESCAPE_SEQUENCE})* ("'"|\\)?
STRING_LITERAL = \" ([^\\\"\r\n] | {ESCAPE_SEQUENCE})* (\"|\\)?

%%

<YYINITIAL> {

  {WHITE_SPACE_CHAR}+ { return JavaTokenType.WHITE_SPACE; }

  {C_STYLE_COMMENT} { return JavaTokenType.C_STYLE_COMMENT; }
  {END_OF_LINE_COMMENT} { return JavaTokenType.END_OF_LINE_COMMENT; }
  {DOC_COMMENT} { return JavaDocElementType.DOC_COMMENT; }

  {LONG_LITERAL} { return JavaTokenType.LONG_LITERAL; }
  {INTEGER_LITERAL} { return JavaTokenType.INTEGER_LITERAL; }
  {FLOAT_LITERAL} { return JavaTokenType.FLOAT_LITERAL; }
  {DOUBLE_LITERAL} { return JavaTokenType.DOUBLE_LITERAL; }
  {CHARACTER_LITERAL} { return JavaTokenType.CHARACTER_LITERAL; }
  {STRING_LITERAL} { return JavaTokenType.STRING_LITERAL; }

  "true" { return JavaTokenType.TRUE_KEYWORD; }
  "false" { return JavaTokenType.FALSE_KEYWORD; }
  "null" { return JavaTokenType.NULL_KEYWORD; }

  "abstract" { return JavaTokenType.ABSTRACT_KEYWORD; }
  "assert" { return myAssertKeyword ? JavaTokenType.ASSERT_KEYWORD : JavaTokenType.IDENTIFIER; }
  "boolean" { return JavaTokenType.BOOLEAN_KEYWORD; }
  "break" { return JavaTokenType.BREAK_KEYWORD; }
  "byte" { return JavaTokenType.BYTE_KEYWORD; }
  "case" { return JavaTokenType.CASE_KEYWORD; }
  "catch" { return JavaTokenType.CATCH_KEYWORD; }
  "char" { return JavaTokenType.CHAR_KEYWORD; }
  "class" { return JavaTokenType.CLASS_KEYWORD; }
  "const" { return JavaTokenType.CONST_KEYWORD; }
  "continue" { return JavaTokenType.CONTINUE_KEYWORD; }
  "default" { return JavaTokenType.DEFAULT_KEYWORD; }
  "do" { return JavaTokenType.DO_KEYWORD; }
  "double" { return JavaTokenType.DOUBLE_KEYWORD; }
  "else" { return JavaTokenType.ELSE_KEYWORD; }
  "enum" { return myEnumKeyword ? JavaTokenType.ENUM_KEYWORD : JavaTokenType.IDENTIFIER; }
  "extends" { return JavaTokenType.EXTENDS_KEYWORD; }
  "final" { return JavaTokenType.FINAL_KEYWORD; }
  "finally" { return JavaTokenType.FINALLY_KEYWORD; }
  "float" { return JavaTokenType.FLOAT_KEYWORD; }
  "for" { return JavaTokenType.FOR_KEYWORD; }
  "goto" { return JavaTokenType.GOTO_KEYWORD; }
  "if" { return JavaTokenType.IF_KEYWORD; }
  "implements" { return JavaTokenType.IMPLEMENTS_KEYWORD; }
  "import" { return JavaTokenType.IMPORT_KEYWORD; }
  "instanceof" { return JavaTokenType.INSTANCEOF_KEYWORD; }
  "int" { return JavaTokenType.INT_KEYWORD; }
  "interface" { return JavaTokenType.INTERFACE_KEYWORD; }
  "long" { return JavaTokenType.LONG_KEYWORD; }
  "native" { return JavaTokenType.NATIVE_KEYWORD; }
  "new" { return JavaTokenType.NEW_KEYWORD; }
  "package" { return JavaTokenType.PACKAGE_KEYWORD; }
  "private" { return JavaTokenType.PRIVATE_KEYWORD; }
  "public" { return JavaTokenType.PUBLIC_KEYWORD; }
  "short" { return JavaTokenType.SHORT_KEYWORD; }
  "super" { return JavaTokenType.SUPER_KEYWORD; }
  "switch" { return JavaTokenType.SWITCH_KEYWORD; }
  "synchronized" { return JavaTokenType.SYNCHRONIZED_KEYWORD; }
  "this" { return JavaTokenType.THIS_KEYWORD; }
  "throw" { return JavaTokenType.THROW_KEYWORD; }
  "protected" { return JavaTokenType.PROTECTED_KEYWORD; }
  "transient" { return JavaTokenType.TRANSIENT_KEYWORD; }
  "return" { return JavaTokenType.RETURN_KEYWORD; }
  "void" { return JavaTokenType.VOID_KEYWORD; }
  "static" { return JavaTokenType.STATIC_KEYWORD; }
  "strictfp" { return JavaTokenType.STRICTFP_KEYWORD; }
  "while" { return JavaTokenType.WHILE_KEYWORD; }
  "try" { return JavaTokenType.TRY_KEYWORD; }
  "volatile" { return JavaTokenType.VOLATILE_KEYWORD; }
  "throws" { return JavaTokenType.THROWS_KEYWORD; }

  {IDENTIFIER} { return JavaTokenType.IDENTIFIER; }

  "==" { return JavaTokenType.EQEQ; }
  "!=" { return JavaTokenType.NE; }
  "||" { return JavaTokenType.OROR; }
  "++" { return JavaTokenType.PLUSPLUS; }
  "--" { return JavaTokenType.MINUSMINUS; }

  "<" { return JavaTokenType.LT; }
  "<=" { return JavaTokenType.LE; }
  "<<=" { return JavaTokenType.LTLTEQ; }
  "<<" { return JavaTokenType.LTLT; }
  ">" { return JavaTokenType.GT; }
  "&" { return JavaTokenType.AND; }
  "&&" { return JavaTokenType.ANDAND; }

  "+=" { return JavaTokenType.PLUSEQ; }
  "-=" { return JavaTokenType.MINUSEQ; }
  "*=" { return JavaTokenType.ASTERISKEQ; }
  "/=" { return JavaTokenType.DIVEQ; }
  "&=" { return JavaTokenType.ANDEQ; }
  "|=" { return JavaTokenType.OREQ; }
  "^=" { return JavaTokenType.XOREQ; }
  "%=" { return JavaTokenType.PERCEQ; }

  "("   { return JavaTokenType.LPARENTH; }
  ")"   { return JavaTokenType.RPARENTH; }
  "{"   { return JavaTokenType.LBRACE; }
  "}"   { return JavaTokenType.RBRACE; }
  "["   { return JavaTokenType.LBRACKET; }
  "]"   { return JavaTokenType.RBRACKET; }
  ";"   { return JavaTokenType.SEMICOLON; }
  ","   { return JavaTokenType.COMMA; }
  "..." { return JavaTokenType.ELLIPSIS; }
  "."   { return JavaTokenType.DOT; }

  "=" { return JavaTokenType.EQ; }
  "!" { return JavaTokenType.EXCL; }
  "~" { return JavaTokenType.TILDE; }
  "?" { return JavaTokenType.QUEST; }
  ":" { return JavaTokenType.COLON; }
  "+" { return JavaTokenType.PLUS; }
  "-" { return JavaTokenType.MINUS; }
  "*" { return JavaTokenType.ASTERISK; }
  "/" { return JavaTokenType.DIV; }
  "|" { return JavaTokenType.OR; }
  "^" { return JavaTokenType.XOR; }
  "%" { return JavaTokenType.PERC; }
  "@" { return JavaTokenType.AT; }

  "::" { return JavaTokenType.DOUBLE_COLON; }
  "->" { return JavaTokenType.ARROW; }

  "<<<<<<<" | "|||||||" | "=======" | ">>>>>>>" { return JavaTokenType.CONFLICT_MARKER; }
}

[^]  { return JavaTokenType.BAD_CHARACTER; }
