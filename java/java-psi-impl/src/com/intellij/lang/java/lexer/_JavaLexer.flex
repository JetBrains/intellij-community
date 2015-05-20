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
    zzPushbackPos = 0;
    zzAtEOF = offset < zzEndRead;
  }
%}

%unicode
%class _JavaLexer
%implements FlexLexer
%function advance
%type IElementType

%eof{
  return;
%eof}

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

<YYINITIAL> {WHITE_SPACE_CHAR}+ { return JavaTokenType.WHITE_SPACE; }

<YYINITIAL> {C_STYLE_COMMENT} { return JavaTokenType.C_STYLE_COMMENT; }
<YYINITIAL> {END_OF_LINE_COMMENT} { return JavaTokenType.END_OF_LINE_COMMENT; }
<YYINITIAL> {DOC_COMMENT} { return JavaDocElementType.DOC_COMMENT; }

<YYINITIAL> {LONG_LITERAL} { return JavaTokenType.LONG_LITERAL; }
<YYINITIAL> {INTEGER_LITERAL} { return JavaTokenType.INTEGER_LITERAL; }
<YYINITIAL> {FLOAT_LITERAL} { return JavaTokenType.FLOAT_LITERAL; }
<YYINITIAL> {DOUBLE_LITERAL} { return JavaTokenType.DOUBLE_LITERAL; }
<YYINITIAL> {CHARACTER_LITERAL} { return JavaTokenType.CHARACTER_LITERAL; }
<YYINITIAL> {STRING_LITERAL} { return JavaTokenType.STRING_LITERAL; }

<YYINITIAL> "true" { return JavaTokenType.TRUE_KEYWORD; }
<YYINITIAL> "false" { return JavaTokenType.FALSE_KEYWORD; }
<YYINITIAL> "null" { return JavaTokenType.NULL_KEYWORD; }

<YYINITIAL> "abstract" { return JavaTokenType.ABSTRACT_KEYWORD; }
<YYINITIAL> "assert" { return myAssertKeyword ? JavaTokenType.ASSERT_KEYWORD : JavaTokenType.IDENTIFIER; }
<YYINITIAL> "boolean" { return JavaTokenType.BOOLEAN_KEYWORD; }
<YYINITIAL> "break" { return JavaTokenType.BREAK_KEYWORD; }
<YYINITIAL> "byte" { return JavaTokenType.BYTE_KEYWORD; }
<YYINITIAL> "case" { return JavaTokenType.CASE_KEYWORD; }
<YYINITIAL> "catch" { return JavaTokenType.CATCH_KEYWORD; }
<YYINITIAL> "char" { return JavaTokenType.CHAR_KEYWORD; }
<YYINITIAL> "class" { return JavaTokenType.CLASS_KEYWORD; }
<YYINITIAL> "const" { return JavaTokenType.CONST_KEYWORD; }
<YYINITIAL> "continue" { return JavaTokenType.CONTINUE_KEYWORD; }
<YYINITIAL> "default" { return JavaTokenType.DEFAULT_KEYWORD; }
<YYINITIAL> "do" { return JavaTokenType.DO_KEYWORD; }
<YYINITIAL> "double" { return JavaTokenType.DOUBLE_KEYWORD; }
<YYINITIAL> "else" { return JavaTokenType.ELSE_KEYWORD; }
<YYINITIAL> "enum" { return myEnumKeyword ? JavaTokenType.ENUM_KEYWORD : JavaTokenType.IDENTIFIER; }
<YYINITIAL> "extends" { return JavaTokenType.EXTENDS_KEYWORD; }
<YYINITIAL> "final" { return JavaTokenType.FINAL_KEYWORD; }
<YYINITIAL> "finally" { return JavaTokenType.FINALLY_KEYWORD; }
<YYINITIAL> "float" { return JavaTokenType.FLOAT_KEYWORD; }
<YYINITIAL> "for" { return JavaTokenType.FOR_KEYWORD; }
<YYINITIAL> "goto" { return JavaTokenType.GOTO_KEYWORD; }
<YYINITIAL> "if" { return JavaTokenType.IF_KEYWORD; }
<YYINITIAL> "implements" { return JavaTokenType.IMPLEMENTS_KEYWORD; }
<YYINITIAL> "import" { return JavaTokenType.IMPORT_KEYWORD; }
<YYINITIAL> "instanceof" { return JavaTokenType.INSTANCEOF_KEYWORD; }
<YYINITIAL> "int" { return JavaTokenType.INT_KEYWORD; }
<YYINITIAL> "interface" { return JavaTokenType.INTERFACE_KEYWORD; }
<YYINITIAL> "long" { return JavaTokenType.LONG_KEYWORD; }
<YYINITIAL> "native" { return JavaTokenType.NATIVE_KEYWORD; }
<YYINITIAL> "new" { return JavaTokenType.NEW_KEYWORD; }
<YYINITIAL> "package" { return JavaTokenType.PACKAGE_KEYWORD; }
<YYINITIAL> "private" { return JavaTokenType.PRIVATE_KEYWORD; }
<YYINITIAL> "public" { return JavaTokenType.PUBLIC_KEYWORD; }
<YYINITIAL> "short" { return JavaTokenType.SHORT_KEYWORD; }
<YYINITIAL> "super" { return JavaTokenType.SUPER_KEYWORD; }
<YYINITIAL> "switch" { return JavaTokenType.SWITCH_KEYWORD; }
<YYINITIAL> "synchronized" { return JavaTokenType.SYNCHRONIZED_KEYWORD; }
<YYINITIAL> "this" { return JavaTokenType.THIS_KEYWORD; }
<YYINITIAL> "throw" { return JavaTokenType.THROW_KEYWORD; }
<YYINITIAL> "protected" { return JavaTokenType.PROTECTED_KEYWORD; }
<YYINITIAL> "transient" { return JavaTokenType.TRANSIENT_KEYWORD; }
<YYINITIAL> "return" { return JavaTokenType.RETURN_KEYWORD; }
<YYINITIAL> "void" { return JavaTokenType.VOID_KEYWORD; }
<YYINITIAL> "static" { return JavaTokenType.STATIC_KEYWORD; }
<YYINITIAL> "strictfp" { return JavaTokenType.STRICTFP_KEYWORD; }
<YYINITIAL> "while" { return JavaTokenType.WHILE_KEYWORD; }
<YYINITIAL> "try" { return JavaTokenType.TRY_KEYWORD; }
<YYINITIAL> "volatile" { return JavaTokenType.VOLATILE_KEYWORD; }
<YYINITIAL> "throws" { return JavaTokenType.THROWS_KEYWORD; }

<YYINITIAL> {IDENTIFIER} { return JavaTokenType.IDENTIFIER; }

<YYINITIAL> "==" { return JavaTokenType.EQEQ; }
<YYINITIAL> "!=" { return JavaTokenType.NE; }
<YYINITIAL> "||" { return JavaTokenType.OROR; }
<YYINITIAL> "++" { return JavaTokenType.PLUSPLUS; }
<YYINITIAL> "--" { return JavaTokenType.MINUSMINUS; }

<YYINITIAL> "<" { return JavaTokenType.LT; }
<YYINITIAL> "<=" { return JavaTokenType.LE; }
<YYINITIAL> "<<=" { return JavaTokenType.LTLTEQ; }
<YYINITIAL> "<<" { return JavaTokenType.LTLT; }
<YYINITIAL> ">" { return JavaTokenType.GT; }
<YYINITIAL> "&" { return JavaTokenType.AND; }
<YYINITIAL> "&&" { return JavaTokenType.ANDAND; }

<YYINITIAL> "+=" { return JavaTokenType.PLUSEQ; }
<YYINITIAL> "-=" { return JavaTokenType.MINUSEQ; }
<YYINITIAL> "*=" { return JavaTokenType.ASTERISKEQ; }
<YYINITIAL> "/=" { return JavaTokenType.DIVEQ; }
<YYINITIAL> "&=" { return JavaTokenType.ANDEQ; }
<YYINITIAL> "|=" { return JavaTokenType.OREQ; }
<YYINITIAL> "^=" { return JavaTokenType.XOREQ; }
<YYINITIAL> "%=" { return JavaTokenType.PERCEQ; }

<YYINITIAL> "("   { return JavaTokenType.LPARENTH; }
<YYINITIAL> ")"   { return JavaTokenType.RPARENTH; }
<YYINITIAL> "{"   { return JavaTokenType.LBRACE; }
<YYINITIAL> "}"   { return JavaTokenType.RBRACE; }
<YYINITIAL> "["   { return JavaTokenType.LBRACKET; }
<YYINITIAL> "]"   { return JavaTokenType.RBRACKET; }
<YYINITIAL> ";"   { return JavaTokenType.SEMICOLON; }
<YYINITIAL> ","   { return JavaTokenType.COMMA; }
<YYINITIAL> "..." { return JavaTokenType.ELLIPSIS; }
<YYINITIAL> "."   { return JavaTokenType.DOT; }

<YYINITIAL> "=" { return JavaTokenType.EQ; }
<YYINITIAL> "!" { return JavaTokenType.EXCL; }
<YYINITIAL> "~" { return JavaTokenType.TILDE; }
<YYINITIAL> "?" { return JavaTokenType.QUEST; }
<YYINITIAL> ":" { return JavaTokenType.COLON; }
<YYINITIAL> "+" { return JavaTokenType.PLUS; }
<YYINITIAL> "-" { return JavaTokenType.MINUS; }
<YYINITIAL> "*" { return JavaTokenType.ASTERISK; }
<YYINITIAL> "/" { return JavaTokenType.DIV; }
<YYINITIAL> "|" { return JavaTokenType.OR; }
<YYINITIAL> "^" { return JavaTokenType.XOR; }
<YYINITIAL> "%" { return JavaTokenType.PERC; }
<YYINITIAL> "@" { return JavaTokenType.AT; }

<YYINITIAL> "::" { return JavaTokenType.DOUBLE_COLON; }
<YYINITIAL> "->" { return JavaTokenType.ARROW; }

<YYINITIAL> . { return JavaTokenType.BAD_CHARACTER; }
