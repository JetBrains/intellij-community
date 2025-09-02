package com.intellij.java.syntax.lexer

import com.intellij.java.syntax.element.JavaSyntaxTokenType
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.element.SyntaxTokenTypes
import com.intellij.platform.syntax.util.lexer.FlexLexer
import com.intellij.pom.java.LanguageLevel
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

@Suppress("ALL")
%%

%{
  private val myAssertKeyword: Boolean
  private val myEnumKeyword: Boolean

  constructor(level: LanguageLevel) {
    myAssertKeyword = level.isAtLeast(LanguageLevel.JDK_1_4)
    myEnumKeyword = level.isAtLeast(LanguageLevel.JDK_1_5)
  }

  public fun goTo(offset: Int) {
    zzCurrentPos = offset
    zzMarkedPos = offset
    zzStartRead = offset
    zzAtEOF = false
  }
%}

%unicode
%class _JavaLexer
%implements FlexLexer
%function advance
%type SyntaxElementType

IDENTIFIER = [:jletter:] [:jletterdigit:]*

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

%%

/*
 * NOTE: the rule set does not include rules for whitespaces, comments, and text literals -
 * they are implemented in com.intellij.lang.java.lexer.JavaLexer class.
 */

<YYINITIAL> {
  {LONG_LITERAL} { return JavaSyntaxTokenType.LONG_LITERAL; }
  {INTEGER_LITERAL} { return JavaSyntaxTokenType.INTEGER_LITERAL; }
  {FLOAT_LITERAL} { return JavaSyntaxTokenType.FLOAT_LITERAL; }
  {DOUBLE_LITERAL} { return JavaSyntaxTokenType.DOUBLE_LITERAL; }

  "true" { return JavaSyntaxTokenType.TRUE_KEYWORD; }
  "false" { return JavaSyntaxTokenType.FALSE_KEYWORD; }
  "null" { return JavaSyntaxTokenType.NULL_KEYWORD; }

  "abstract" { return JavaSyntaxTokenType.ABSTRACT_KEYWORD; }
  "assert" { return if (myAssertKeyword) JavaSyntaxTokenType.ASSERT_KEYWORD else JavaSyntaxTokenType.IDENTIFIER; }
  "boolean" { return JavaSyntaxTokenType.BOOLEAN_KEYWORD; }
  "break" { return JavaSyntaxTokenType.BREAK_KEYWORD; }
  "byte" { return JavaSyntaxTokenType.BYTE_KEYWORD; }
  "case" { return JavaSyntaxTokenType.CASE_KEYWORD; }
  "catch" { return JavaSyntaxTokenType.CATCH_KEYWORD; }
  "char" { return JavaSyntaxTokenType.CHAR_KEYWORD; }
  "class" { return JavaSyntaxTokenType.CLASS_KEYWORD; }
  "const" { return JavaSyntaxTokenType.CONST_KEYWORD; }
  "continue" { return JavaSyntaxTokenType.CONTINUE_KEYWORD; }
  "default" { return JavaSyntaxTokenType.DEFAULT_KEYWORD; }
  "do" { return JavaSyntaxTokenType.DO_KEYWORD; }
  "double" { return JavaSyntaxTokenType.DOUBLE_KEYWORD; }
  "else" { return JavaSyntaxTokenType.ELSE_KEYWORD; }
  "enum" { return if (myEnumKeyword) JavaSyntaxTokenType.ENUM_KEYWORD else JavaSyntaxTokenType.IDENTIFIER; }
  "extends" { return JavaSyntaxTokenType.EXTENDS_KEYWORD; }
  "final" { return JavaSyntaxTokenType.FINAL_KEYWORD; }
  "finally" { return JavaSyntaxTokenType.FINALLY_KEYWORD; }
  "float" { return JavaSyntaxTokenType.FLOAT_KEYWORD; }
  "for" { return JavaSyntaxTokenType.FOR_KEYWORD; }
  "goto" { return JavaSyntaxTokenType.GOTO_KEYWORD; }
  "if" { return JavaSyntaxTokenType.IF_KEYWORD; }
  "implements" { return JavaSyntaxTokenType.IMPLEMENTS_KEYWORD; }
  "import" { return JavaSyntaxTokenType.IMPORT_KEYWORD; }
  "instanceof" { return JavaSyntaxTokenType.INSTANCEOF_KEYWORD; }
  "int" { return JavaSyntaxTokenType.INT_KEYWORD; }
  "interface" { return JavaSyntaxTokenType.INTERFACE_KEYWORD; }
  "long" { return JavaSyntaxTokenType.LONG_KEYWORD; }
  "native" { return JavaSyntaxTokenType.NATIVE_KEYWORD; }
  "new" { return JavaSyntaxTokenType.NEW_KEYWORD; }
  "package" { return JavaSyntaxTokenType.PACKAGE_KEYWORD; }
  "private" { return JavaSyntaxTokenType.PRIVATE_KEYWORD; }
  "public" { return JavaSyntaxTokenType.PUBLIC_KEYWORD; }
  "short" { return JavaSyntaxTokenType.SHORT_KEYWORD; }
  "super" { return JavaSyntaxTokenType.SUPER_KEYWORD; }
  "switch" { return JavaSyntaxTokenType.SWITCH_KEYWORD; }
  "synchronized" { return JavaSyntaxTokenType.SYNCHRONIZED_KEYWORD; }
  "this" { return JavaSyntaxTokenType.THIS_KEYWORD; }
  "throw" { return JavaSyntaxTokenType.THROW_KEYWORD; }
  "protected" { return JavaSyntaxTokenType.PROTECTED_KEYWORD; }
  "transient" { return JavaSyntaxTokenType.TRANSIENT_KEYWORD; }
  "return" { return JavaSyntaxTokenType.RETURN_KEYWORD; }
  "void" { return JavaSyntaxTokenType.VOID_KEYWORD; }
  "static" { return JavaSyntaxTokenType.STATIC_KEYWORD; }
  "strictfp" { return JavaSyntaxTokenType.STRICTFP_KEYWORD; }
  "while" { return JavaSyntaxTokenType.WHILE_KEYWORD; }
  "try" { return JavaSyntaxTokenType.TRY_KEYWORD; }
  "volatile" { return JavaSyntaxTokenType.VOLATILE_KEYWORD; }
  "throws" { return JavaSyntaxTokenType.THROWS_KEYWORD; }

  {IDENTIFIER} { return JavaSyntaxTokenType.IDENTIFIER; }

  "==" { return JavaSyntaxTokenType.EQEQ; }
  "!=" { return JavaSyntaxTokenType.NE; }
  "||" { return JavaSyntaxTokenType.OROR; }
  "++" { return JavaSyntaxTokenType.PLUSPLUS; }
  "--" { return JavaSyntaxTokenType.MINUSMINUS; }

  "<" { return JavaSyntaxTokenType.LT; }
  "<=" { return JavaSyntaxTokenType.LE; }
  "<<=" { return JavaSyntaxTokenType.LTLTEQ; }
  "<<" { return JavaSyntaxTokenType.LTLT; }
  ">" { return JavaSyntaxTokenType.GT; }
  "&" { return JavaSyntaxTokenType.AND; }
  "&&" { return JavaSyntaxTokenType.ANDAND; }

  "+=" { return JavaSyntaxTokenType.PLUSEQ; }
  "-=" { return JavaSyntaxTokenType.MINUSEQ; }
  "*=" { return JavaSyntaxTokenType.ASTERISKEQ; }
  "/=" { return JavaSyntaxTokenType.DIVEQ; }
  "&=" { return JavaSyntaxTokenType.ANDEQ; }
  "|=" { return JavaSyntaxTokenType.OREQ; }
  "^=" { return JavaSyntaxTokenType.XOREQ; }
  "%=" { return JavaSyntaxTokenType.PERCEQ; }

  "("   { return JavaSyntaxTokenType.LPARENTH; }
  ")"   { return JavaSyntaxTokenType.RPARENTH; }
  "{"   { return JavaSyntaxTokenType.LBRACE; }
  "}"   { return JavaSyntaxTokenType.RBRACE; }
  "["   { return JavaSyntaxTokenType.LBRACKET; }
  "]"   { return JavaSyntaxTokenType.RBRACKET; }
  ";"   { return JavaSyntaxTokenType.SEMICOLON; }
  ","   { return JavaSyntaxTokenType.COMMA; }
  "..." { return JavaSyntaxTokenType.ELLIPSIS; }
  "."   { return JavaSyntaxTokenType.DOT; }

  "=" { return JavaSyntaxTokenType.EQ; }
  "!" { return JavaSyntaxTokenType.EXCL; }
  "~" { return JavaSyntaxTokenType.TILDE; }
  "?" { return JavaSyntaxTokenType.QUEST; }
  ":" { return JavaSyntaxTokenType.COLON; }
  "+" { return JavaSyntaxTokenType.PLUS; }
  "-" { return JavaSyntaxTokenType.MINUS; }
  "*" { return JavaSyntaxTokenType.ASTERISK; }
  "/" { return JavaSyntaxTokenType.DIV; }
  "|" { return JavaSyntaxTokenType.OR; }
  "^" { return JavaSyntaxTokenType.XOR; }
  "%" { return JavaSyntaxTokenType.PERC; }
  "@" { return JavaSyntaxTokenType.AT; }

  "::" { return JavaSyntaxTokenType.DOUBLE_COLON; }
  "->" { return JavaSyntaxTokenType.ARROW; }
}

[^]  { return SyntaxTokenTypes.BAD_CHARACTER; }
