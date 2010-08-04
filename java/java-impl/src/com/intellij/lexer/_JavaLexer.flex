/* It's an automatically generated code. Do not modify it. */
package com.intellij.lexer;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.tree.IElementType;

%%

%{
  private boolean myAssertKeywordEnabled;
  private boolean myJdk15Enabled;

  public _JavaLexer(boolean isAssertKeywordEnabled, boolean jdk15Enabled){
    this((java.io.Reader)null);
    myAssertKeywordEnabled = isAssertKeywordEnabled;
    myJdk15Enabled = jdk15Enabled;
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
%eof{  return;
%eof}

DIGIT=[0-9]
OCTAL_DIGIT=[0-7]
HEX_DIGIT=[0-9A-Fa-f]
WHITE_SPACE_CHAR=[\ \n\r\t\f]

IDENTIFIER=[:jletter:] [:jletterdigit:]*

C_STYLE_COMMENT=("/*"[^"*"]{COMMENT_TAIL})|"/*"
DOC_COMMENT="/*""*"+("/"|([^"/""*"]{COMMENT_TAIL}))?
COMMENT_TAIL=([^"*"]*("*"+[^"*""/"])?)*("*"+"/")?
END_OF_LINE_COMMENT="/""/"[^\r\n]*

INTEGER_LITERAL={DECIMAL_INTEGER_LITERAL}|{HEX_INTEGER_LITERAL}|{OCTAL_INTEGER_LITERAL}
DECIMAL_INTEGER_LITERAL=(0|([1-9]({DIGIT})*))
HEX_INTEGER_LITERAL=0[Xx]({HEX_DIGIT})*
OCTAL_INTEGER_LITERAL=0({OCTAL_DIGIT})+
LONG_LITERAL=({INTEGER_LITERAL})[Ll]

FLOAT_LITERAL=(({FLOATING_POINT_LITERAL1})[Ff])|(({FLOATING_POINT_LITERAL2})[Ff])|(({FLOATING_POINT_LITERAL3})[Ff])|(({FLOATING_POINT_LITERAL4})[Ff])
DOUBLE_LITERAL=(({FLOATING_POINT_LITERAL1})[Dd]?)|(({FLOATING_POINT_LITERAL2})[Dd]?)|(({FLOATING_POINT_LITERAL3})[Dd]?)|(({FLOATING_POINT_LITERAL4})[Dd])
FLOATING_POINT_LITERAL1=({DIGIT})+"."({DIGIT})*({EXPONENT_PART})?
FLOATING_POINT_LITERAL2="."({DIGIT})+({EXPONENT_PART})?
FLOATING_POINT_LITERAL3=({DIGIT})+({EXPONENT_PART})
FLOATING_POINT_LITERAL4=({DIGIT})+
EXPONENT_PART=[Ee]["+""-"]?({DIGIT})*
HEX_FLOAT_LITERAL={HEX_SIGNIFICAND}{BINARY_EXPONENT}[Ff]
HEX_DOUBLE_LITERAL={HEX_SIGNIFICAND}{BINARY_EXPONENT}[Dd]?
BINARY_EXPONENT=[Pp][+-]?{DIGIT}+
HEX_SIGNIFICAND={HEX_INTEGER_LITERAL}|{HEX_INTEGER_LITERAL}.|0[Xx]{HEX_DIGIT}*.{HEX_DIGIT}+

CHARACTER_LITERAL="'"([^\\\'\r\n]|{ESCAPE_SEQUENCE})*("'"|\\)?
STRING_LITERAL=\"([^\\\"\r\n]|{ESCAPE_SEQUENCE})*(\"|\\)?
ESCAPE_SEQUENCE=\\[^\r\n]

%%

<YYINITIAL> {WHITE_SPACE_CHAR}+ { return JavaTokenType.WHITE_SPACE; }

<YYINITIAL> {C_STYLE_COMMENT} { return JavaTokenType.C_STYLE_COMMENT; }
<YYINITIAL> {END_OF_LINE_COMMENT} { return JavaTokenType.END_OF_LINE_COMMENT; }
<YYINITIAL> {DOC_COMMENT} { return JavaDocElementType.DOC_COMMENT; }

<YYINITIAL> {LONG_LITERAL} { return JavaTokenType.LONG_LITERAL; }
<YYINITIAL> {INTEGER_LITERAL} { return JavaTokenType.INTEGER_LITERAL; }
<YYINITIAL> {FLOAT_LITERAL} { return JavaTokenType.FLOAT_LITERAL; }
<YYINITIAL> {HEX_FLOAT_LITERAL} { if (myJdk15Enabled) return JavaTokenType.FLOAT_LITERAL; }
<YYINITIAL> {DOUBLE_LITERAL} { return JavaTokenType.DOUBLE_LITERAL; }
<YYINITIAL> {HEX_DOUBLE_LITERAL} { if (myJdk15Enabled) return JavaTokenType.DOUBLE_LITERAL; }

<YYINITIAL> {CHARACTER_LITERAL} { return JavaTokenType.CHARACTER_LITERAL; }
<YYINITIAL> {STRING_LITERAL} { return JavaTokenType.STRING_LITERAL; }

<YYINITIAL> "true" { return JavaTokenType.TRUE_KEYWORD; }
<YYINITIAL> "false" { return JavaTokenType.FALSE_KEYWORD; }
<YYINITIAL> "null" { return JavaTokenType.NULL_KEYWORD; }

<YYINITIAL> "abstract" { return JavaTokenType.ABSTRACT_KEYWORD; }
<YYINITIAL> "assert" { return myAssertKeywordEnabled ? JavaTokenType.ASSERT_KEYWORD : JavaTokenType.IDENTIFIER; }
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
<YYINITIAL> "enum" { return myJdk15Enabled ? JavaTokenType.ENUM_KEYWORD : JavaTokenType.IDENTIFIER; }
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

<YYINITIAL> . { return JavaTokenType.BAD_CHARACTER; }

