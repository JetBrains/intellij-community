/* It's an automatically generated code. Do not modify it. */
package org.intellij.lang.regexp;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;

import com.intellij.util.containers.IntArrayList;
import java.util.EnumSet;

import static org.intellij.lang.regexp.RegExpCapability.*;

@SuppressWarnings("ALL")
%%

%class _RegExLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType

%{
    // This adds support for nested states. I'm no JFlex pro, so maybe this is overkill, but it works quite well.
    final IntArrayList states = new IntArrayList();

    // This was an idea to use the regex implementation for XML schema regexes (which use a slightly different syntax)
    // as well, but is currently unfinished as it requires to tweak more places than just the lexer.
    private boolean xmlSchemaMode;

    int capturingGroupCount = 0;

    private Boolean allowDanglingMetacharacters;
    private boolean allowOmitNumbersInQuantifiers;
    private boolean allowOmitBothNumbersInQuantifiers;
    private boolean allowNestedCharacterClasses;
    private boolean allowOctalNoLeadingZero;
    private boolean allowHexDigitClass;
    private boolean allowEmptyCharacterClass;
    private boolean allowHorizontalWhitespaceClass;
    private boolean allowPosixBracketExpressions;
    private boolean allowTransformationEscapes;
    private boolean allowExtendedUnicodeCharacter;
    private boolean allowOneHexCharEscape;
    private boolean allowMysqlBracketExpressions;
    private int maxOctal = 0777;
    private int minOctalDigits = 1;

    _RegExLexer(EnumSet<RegExpCapability> capabilities) {
      this((java.io.Reader)null);
      this.xmlSchemaMode = capabilities.contains(XML_SCHEMA_MODE);
      if (capabilities.contains(DANGLING_METACHARACTERS)) this.allowDanglingMetacharacters = Boolean.TRUE;
      if (capabilities.contains(NO_DANGLING_METACHARACTERS)) this.allowDanglingMetacharacters = Boolean.FALSE;
      this.allowOmitNumbersInQuantifiers = capabilities.contains(OMIT_NUMBERS_IN_QUANTIFIERS);
      this.allowOmitBothNumbersInQuantifiers = capabilities.contains(OMIT_BOTH_NUMBERS_IN_QUANTIFIERS);
      this.allowNestedCharacterClasses = capabilities.contains(NESTED_CHARACTER_CLASSES);
      this.allowOctalNoLeadingZero = capabilities.contains(OCTAL_NO_LEADING_ZERO);
      this.commentMode = capabilities.contains(COMMENT_MODE);
      this.allowHexDigitClass = capabilities.contains(ALLOW_HEX_DIGIT_CLASS);
      this.allowHorizontalWhitespaceClass = capabilities.contains(ALLOW_HORIZONTAL_WHITESPACE_CLASS);
      this.allowEmptyCharacterClass = capabilities.contains(ALLOW_EMPTY_CHARACTER_CLASS);
      this.allowPosixBracketExpressions = capabilities.contains(POSIX_BRACKET_EXPRESSIONS);
      this.allowTransformationEscapes = capabilities.contains(TRANSFORMATION_ESCAPES);
      this.allowMysqlBracketExpressions = capabilities.contains(MYSQL_BRACKET_EXPRESSIONS);
      if (capabilities.contains(MAX_OCTAL_177)) {
        maxOctal = 0177;
      }
      else if (capabilities.contains(MAX_OCTAL_377)) {
        maxOctal = 0377;
      }
      if (capabilities.contains(MIN_OCTAL_2_DIGITS)) {
        minOctalDigits = 2;
      }
      else if (capabilities.contains(MIN_OCTAL_3_DIGITS)) {
        minOctalDigits = 3;
      }
      this.allowExtendedUnicodeCharacter = capabilities.contains(EXTENDED_UNICODE_CHARACTER);
      this.allowOneHexCharEscape = capabilities.contains(ONE_HEX_CHAR_ESCAPE);
    }

    private void yypushstate(int state) {
        states.add(yystate());
        yybegin(state);
    }

    private void yypopstate() {
        final int state = states.remove(states.size() - 1);
        yybegin(state);
    }

    private void handleOptions() {
      final String o = yytext().toString();
      if (o.contains("x")) {
        commentMode = !o.startsWith("-");
      }
    }

    // tracks whether the lexer is in comment mode, i.e. whether whitespace is not significant and whether to ignore
    // text after '#' till EOL
    boolean commentMode = false;
%}

%xstate QUOTED
%xstate EMBRACED
%xstate QUANTIFIER
%xstate NEGATED_CLASS
%xstate QUOTED_CLASS1
%xstate CLASS1
%state CLASS2
%state PROP
%state NAMED
%xstate OPTIONS
%xstate COMMENT
%xstate NAMED_GROUP
%xstate QUOTED_NAMED_GROUP
%xstate PY_NAMED_GROUP_REF
%xstate PY_COND_REF
%xstate BRACKET_EXPRESSION
%xstate MYSQL_CHAR_EXPRESSION
%xstate MYSQL_CHAR_EQ_EXPRESSION
%xstate EMBRACED_HEX

DOT="."
LPAREN="("
RPAREN=")"
LBRACE="{"
RBRACE="}"
LBRACKET="["
RBRACKET="]"

ESCAPE="\\"
NAME=[:letter:]([:letter:]|_|-|" "|"("|")"|[:digit:])*
GROUP_NAME=[:letter:]([:letter:]|_|-|" "|[:digit:])*
MYSQL_CHAR_NAME=[:letter:](-|[:letter:])*[:digit:]?
ANY=[^]

META1 = {ESCAPE} | {LBRACKET}
META2= {DOT} | "$" | "?" | "*" | "+" | "|" | "^" | {LBRACE} | {LPAREN} | {RPAREN}

CONTROL="t" | "n" | "r" | "f" | "a" | "e"
BOUNDARY="b" | "b{g}"| "B" | "A" | "z" | "Z" | "G"

CLASS="w" | "W" | "s" | "S" | "d" | "D" | "v" | "V" | "X"
XML_CLASS="c" | "C" | "i" | "I"
PROP="p" | "P"
TRANSFORMATION= "l" | "L" | "U" | "E"

HEX_CHAR=[0-9a-fA-F]

%%

{ESCAPE} "Q"         { yypushstate(QUOTED); return RegExpTT.QUOTE_BEGIN; }

<QUOTED> {
  {ESCAPE} "E"       { yypopstate(); return RegExpTT.QUOTE_END; }
  {ANY}              { return RegExpTT.CHARACTER; }
}

/* \\ */
{ESCAPE} {ESCAPE}    { return RegExpTT.ESC_CHARACTER; }

/* hex escapes */
{ESCAPE} "x" {HEX_CHAR}{2}  {  return RegExpTT.HEX_CHAR; }
{ESCAPE} "x" {HEX_CHAR}     {  if (allowOneHexCharEscape) { return RegExpTT.HEX_CHAR; } else { yypushback(1); return RegExpTT.BAD_HEX_VALUE; }}
{ESCAPE} "x" / {LBRACE}     {  if (allowExtendedUnicodeCharacter) yypushstate(EMBRACED_HEX); else return RegExpTT.BAD_HEX_VALUE;  }
{ESCAPE} "x"                {  return RegExpTT.BAD_HEX_VALUE;  }

/* unicode escapes */
{ESCAPE} "u" ({HEX_CHAR}{4})  { return RegExpTT.UNICODE_CHAR; }
{ESCAPE} "u" / {LBRACE}       {  if (allowExtendedUnicodeCharacter) yypushstate(EMBRACED_HEX); else return StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN;  }
{ESCAPE} "u"                  { return allowTransformationEscapes ? RegExpTT.CHAR_CLASS : StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN; }
{ESCAPE} "u" {HEX_CHAR}{1,3}  { yypushback(yylength() - 2); return StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN; }

<EMBRACED_HEX> {
  {LBRACE}{HEX_CHAR}+{RBRACE}  {  yypopstate(); return (yycharat(-1) == 'u') ? RegExpTT.UNICODE_CHAR : RegExpTT.HEX_CHAR;  }
  {LBRACE}{RBRACE}             {  yypopstate(); return (yycharat(-1) == 'u') ? StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN : RegExpTT.BAD_HEX_VALUE;  }
  {LBRACE}{HEX_CHAR}*          {  yypopstate(); return (yycharat(-1) == 'u') ? StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN : RegExpTT.BAD_HEX_VALUE;  }
}

/* octal escapes */
{ESCAPE} "0" [0-7]{1,2}      { return RegExpTT.OCT_CHAR; }
/* no more than decimal 255 */
{ESCAPE} "0" [0-3][0-7]{2}   { if (allowOctalNoLeadingZero) yypushback(1); return RegExpTT.OCT_CHAR; }
{ESCAPE} "0"                 { return (allowOctalNoLeadingZero ? RegExpTT.OCT_CHAR : RegExpTT.BAD_OCT_VALUE); }

/* single character after "\c" */
{ESCAPE} "c" {ANY}           { if (xmlSchemaMode) { yypushback(1); return RegExpTT.CHAR_CLASS; } else return RegExpTT.CTRL; }

{ESCAPE} {XML_CLASS}         { if (xmlSchemaMode) return RegExpTT.CHAR_CLASS; else return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN; }


/* 999 back references should be enough for everybody */
{ESCAPE} [1-9][0-9]{0,2}      { String text = yytext().toString().substring(1);
                                if (allowOctalNoLeadingZero) {
                                  if (Integer.parseInt(text) <= capturingGroupCount && yystate() != CLASS2) return RegExpTT.BACKREF;
                                  int i = 0;
                                  int value = 0;
                                  for (; i < text.length(); i++) {
                                    char c = text.charAt(i);
                                    if (c > '7') break;
                                    value = value * 8 + (c - '0');
                                  }
                                  if (i > 0) {
                                    yypushback(text.length() - i);
                                    if (value > maxOctal) {
                                      yypushback(1);
                                      return RegExpTT.BAD_OCT_VALUE;
                                    }
                                    if (minOctalDigits > i && yystate() != CLASS2) {
                                      return RegExpTT.BAD_OCT_VALUE;
                                    }
                                    return RegExpTT.OCT_CHAR;
                                  }
                                  return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN;
                                }
                                else {
                                  if (yystate() == CLASS2) {
                                    yypushback(yylength() - 2);
                                    return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN;
                                  }
                                  /* java.util.regex.Pattern says about backrefs:
                                      "In this class, \1 through \9 are always interpreted as back references,
                                      and a larger number is accepted as a back reference if at least that many
                                      subexpressions exist at that point in the regular expression, otherwise the
                                      parser will drop digits until the number is smaller or equal to the existing
                                      number of groups or it is one digit."
                                  */
                                  while (yylength() > 2 && Integer.parseInt(yytext().toString().substring(1)) > capturingGroupCount) {
                                    yypushback(1);
                                  }
                                  return RegExpTT.BACKREF;
                                }
                              }

{ESCAPE}  "-"                 { return (yystate() == CLASS2) ? RegExpTT.ESC_CHARACTER : RegExpTT.REDUNDANT_ESCAPE; }

<YYINITIAL> {
  {ESCAPE}  {LBRACE} / [:digit:]+ {RBRACE}                { return RegExpTT.ESC_CHARACTER; }
  {ESCAPE}  {LBRACE} / [:digit:]+ "," [:digit:]* {RBRACE} { return RegExpTT.ESC_CHARACTER; }
  {ESCAPE}  {LBRACE} / "," [:digit:]+ {RBRACE}            { return allowOmitNumbersInQuantifiers ? RegExpTT.ESC_CHARACTER : RegExpTT.REDUNDANT_ESCAPE; }
  {ESCAPE}  {LBRACE} / "," {RBRACE}                       { return allowOmitBothNumbersInQuantifiers ? RegExpTT.ESC_CHARACTER : RegExpTT.REDUNDANT_ESCAPE; }

  {ESCAPE}  {LBRACE}            { return (allowDanglingMetacharacters != Boolean.TRUE) ? RegExpTT.ESC_CHARACTER : RegExpTT.REDUNDANT_ESCAPE; }
  {ESCAPE}  {RBRACE}            { return (allowDanglingMetacharacters == Boolean.FALSE) ? RegExpTT.ESC_CHARACTER : RegExpTT.REDUNDANT_ESCAPE; }
  {ESCAPE}  {META2}             { return RegExpTT.ESC_CHARACTER; }
}
{ESCAPE}  {META1}             { return RegExpTT.ESC_CHARACTER; }
{ESCAPE}  {CLASS}             { return RegExpTT.CHAR_CLASS;    }
{ESCAPE}  {PROP}              { yypushstate(PROP); return RegExpTT.PROPERTY;      }
{ESCAPE}  {CONTROL}           { return RegExpTT.ESC_CTRL_CHARACTER; }

{ESCAPE}  [hH]                 { return (allowHexDigitClass || allowHorizontalWhitespaceClass ? RegExpTT.CHAR_CLASS : StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN); }
{ESCAPE}  "N"                  { yypushstate(NAMED); return RegExpTT.NAMED_CHARACTER; }
{ESCAPE}  {TRANSFORMATION}     { return allowTransformationEscapes ? RegExpTT.CHAR_CLASS : StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN; }
{ESCAPE}  [\n\b\t\r\f ]        { return commentMode ? RegExpTT.ESC_CTRL_CHARACTER : RegExpTT.REDUNDANT_ESCAPE; }

<CLASS2> {
  {ESCAPE}  {RBRACKET}        { return RegExpTT.ESC_CHARACTER; }
  {ESCAPE}  "b"               { return RegExpTT.ESC_CTRL_CHARACTER; } /* = backspace inside character class under python, ruby, javascript */
}

<YYINITIAL> {
  {ESCAPE}  "k<"                 { yybegin(NAMED_GROUP); return RegExpTT.RUBY_NAMED_GROUP_REF; }
  {ESCAPE}  "k'"                 { yybegin(QUOTED_NAMED_GROUP); return RegExpTT.RUBY_QUOTED_NAMED_GROUP_REF; }
  {ESCAPE}  "g<"                 { yybegin(NAMED_GROUP); return RegExpTT.RUBY_NAMED_GROUP_CALL; }
  {ESCAPE}  "g'"                 { yybegin(QUOTED_NAMED_GROUP); return RegExpTT.RUBY_QUOTED_NAMED_GROUP_CALL; }
  {ESCAPE}  "R"                  { return RegExpTT.CHAR_CLASS; }
  {ESCAPE}  {BOUNDARY}          { return RegExpTT.BOUNDARY; }
}

{ESCAPE}  [A-Za-z]            { return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN; }
{ESCAPE}  {ANY}               { return RegExpTT.REDUNDANT_ESCAPE; }

{ESCAPE}                      { return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN; }

<PROP> {
  {LBRACE}                    { yypopstate(); yypushstate(EMBRACED); return RegExpTT.LBRACE; }
  "L"|"M"|"Z"|"S"|"N"|"P"|"C" { yypopstate(); return RegExpTT.CATEGORY_SHORT_HAND; }
  {ANY}                       { yypopstate(); yypushback(1); }
}

<NAMED> {
  {LBRACE}                    { yypopstate(); yypushstate(EMBRACED); return RegExpTT.LBRACE; }
  {ANY}                       { yypopstate(); yypushback(1); }
}

<YYINITIAL> {
  /* "{" \d+(,\d*)? "}" */
  /* "}" outside counted quantifier is treated as regular character */
  {LBRACE} / [:digit:]+ {RBRACE}                { yypushstate(QUANTIFIER); return RegExpTT.LBRACE; }
  {LBRACE} / [:digit:]+ "," [:digit:]* {RBRACE} { yypushstate(QUANTIFIER); return RegExpTT.LBRACE; }
  {LBRACE} / "," [:digit:]+ {RBRACE}            { if (allowOmitNumbersInQuantifiers || allowDanglingMetacharacters != Boolean.TRUE) { yypushstate(QUANTIFIER); return RegExpTT.LBRACE; } else return RegExpTT.CHARACTER; }
  {LBRACE} / "," {RBRACE}                       { if (allowOmitBothNumbersInQuantifiers || allowDanglingMetacharacters != Boolean.TRUE) { yypushstate(QUANTIFIER); return RegExpTT.LBRACE; } else return RegExpTT.CHARACTER; }
  {LBRACE}  { if (allowDanglingMetacharacters != Boolean.TRUE) { yypushstate(QUANTIFIER); return RegExpTT.LBRACE; } return RegExpTT.CHARACTER;  }
}

<QUANTIFIER> {
  [:digit:]+          { return RegExpTT.NUMBER; }
  ","                 { return RegExpTT.COMMA;  }
  {RBRACE}            { yypopstate(); return RegExpTT.RBRACE; }
  {ANY}               { yypopstate(); yypushback(1); }
}
<EMBRACED> {
  "^"                 { return RegExpTT.CARET;  }
  {NAME}              { return RegExpTT.NAME;   }
  {RBRACE}            { yypopstate(); return RegExpTT.RBRACE; }
  {ANY}               { yypopstate(); yypushback(1); }
}

<YYINITIAL> {
  {LBRACKET} / "^"      { yypushstate(NEGATED_CLASS); return RegExpTT.CLASS_BEGIN; }
  {LBRACKET}            { yypushstate(CLASS1); return RegExpTT.CLASS_BEGIN; }
}

/* []abc] is legal. The first ] is treated as literal character */
<NEGATED_CLASS> {
  "^"  { yybegin(CLASS1); return RegExpTT.CARET; }
}

<QUOTED_CLASS1> {
  "\\E"              { yypopstate(); return RegExpTT.QUOTE_END; }
  {ANY}              { states.set(states.size() - 1, CLASS2); return RegExpTT.CHARACTER; }
}

<CLASS1> {
  {ESCAPE} "^"               { yybegin(CLASS2); return RegExpTT.ESC_CHARACTER; }
  {ESCAPE} "Q"               { yypushstate(QUOTED_CLASS1); return RegExpTT.QUOTE_BEGIN; }
  {ESCAPE} {RBRACKET}        { yybegin(CLASS2); return allowEmptyCharacterClass ? RegExpTT.ESC_CHARACTER : RegExpTT.REDUNDANT_ESCAPE; }
  {RBRACKET}                 { if (allowEmptyCharacterClass) { yypopstate(); return RegExpTT.CLASS_END; } yybegin(CLASS2); return RegExpTT.CHARACTER; }
  {LBRACKET} / ":"           { yybegin(CLASS2); if (allowPosixBracketExpressions) { yypushback(1); } else if (allowNestedCharacterClasses) { yypushstate(CLASS1); return RegExpTT.CLASS_BEGIN; } else { return RegExpTT.CHARACTER; } }
  {LBRACKET} / [.=]          { yybegin(CLASS2); if (allowMysqlBracketExpressions) { yypushback(1); } else if (allowNestedCharacterClasses) { yypushstate(CLASS1); return RegExpTT.CLASS_BEGIN; } else { return RegExpTT.CHARACTER; } }
  {LBRACKET} / "^"           { yybegin(CLASS2); if (allowNestedCharacterClasses) { yypushstate(NEGATED_CLASS); return RegExpTT.CLASS_BEGIN; } return RegExpTT.CHARACTER; }
  {LBRACKET}                 { yybegin(CLASS2); if (allowNestedCharacterClasses) { yypushstate(CLASS1); return RegExpTT.CLASS_BEGIN; } return RegExpTT.CHARACTER; }
  [\n\b\t\r\f ]              { if (commentMode) return com.intellij.psi.TokenType.WHITE_SPACE; yypushback(1); yybegin(CLASS2); }
  {ANY}                      { yypushback(1); yybegin(CLASS2); }
}

<CLASS2> {
  {LBRACKET} [:=.]      { char c = yycharat(1);
                          if (allowPosixBracketExpressions && c == ':') {
                            yybegin(BRACKET_EXPRESSION);
                            return RegExpTT.BRACKET_EXPRESSION_BEGIN;
                          } else if (allowMysqlBracketExpressions && c == '=') {
                            yybegin(MYSQL_CHAR_EQ_EXPRESSION);
                            return RegExpTT.MYSQL_CHAR_EQ_BEGIN;
                          } else if (allowMysqlBracketExpressions && c == '.') {
                            yybegin(MYSQL_CHAR_EXPRESSION);
                            return RegExpTT.MYSQL_CHAR_BEGIN;
                          } else {
                            yypushback(1);
                            return allowNestedCharacterClasses ? RegExpTT.CLASS_BEGIN : RegExpTT.CHARACTER;
                          }
                        }
  {LBRACKET} / "^"      { if (allowNestedCharacterClasses) { yypushstate(NEGATED_CLASS); return RegExpTT.CLASS_BEGIN; } return RegExpTT.CHARACTER; }
  {LBRACKET}            { if (allowNestedCharacterClasses) { yypushstate(CLASS1); return RegExpTT.CLASS_BEGIN; } return RegExpTT.CHARACTER; }
  {RBRACKET}            { yypopstate(); return RegExpTT.CLASS_END; }
  "&&"                  { if (allowNestedCharacterClasses) return RegExpTT.ANDAND; else yypushback(1); return RegExpTT.CHARACTER; }
  "-"                   { return RegExpTT.MINUS; }
  " "                   { return commentMode ? com.intellij.psi.TokenType.WHITE_SPACE : RegExpTT.CHARACTER; }
  [\n\b\t\r\f]          { return commentMode ? com.intellij.psi.TokenType.WHITE_SPACE : RegExpTT.CTRL_CHARACTER; }
  {ANY}                 { return RegExpTT.CHARACTER; }
}

<BRACKET_EXPRESSION> {
  "^"                                     { return RegExpTT.CARET; }
  {NAME}                                  { return RegExpTT.NAME;   }
  ":" {RBRACKET}                          { yybegin(CLASS2); return RegExpTT.BRACKET_EXPRESSION_END; }
  [<>]                                    { return allowMysqlBracketExpressions ? RegExpTT.NAME : RegExpTT.BAD_CHARACTER; }
  {ANY}                                   { return RegExpTT.BAD_CHARACTER; }
}

<MYSQL_CHAR_EXPRESSION> {
  {MYSQL_CHAR_NAME}                       { return RegExpTT.NAME;   }
  "." {RBRACKET}                          { yybegin(CLASS2); return RegExpTT.MYSQL_CHAR_END; }
  {ANY} / "." {RBRACKET}                  { return RegExpTT.CHARACTER; }
  {ANY}                                   { return RegExpTT.BAD_CHARACTER; }
}

<MYSQL_CHAR_EQ_EXPRESSION> {
  {NAME}                                  { return RegExpTT.NAME;   }
  "=" {RBRACKET}                          { yybegin(CLASS2); return RegExpTT.MYSQL_CHAR_EQ_END; }
  {ANY} / "=" {RBRACKET}                  { return RegExpTT.CHARACTER; }
  {ANY}                                   { return RegExpTT.BAD_CHARACTER; }
}

<YYINITIAL> {
  {LPAREN}      { capturingGroupCount++; return RegExpTT.GROUP_BEGIN; }
  {RPAREN}      { return RegExpTT.GROUP_END;   }
  {RBRACE}      { return (allowDanglingMetacharacters != Boolean.FALSE) ? RegExpTT.CHARACTER : RegExpTT.RBRACE;  }

  "|"           { return RegExpTT.UNION;  }
  "?"           { return RegExpTT.QUEST;  }
  "*"           { return RegExpTT.STAR;   }
  "+"           { return RegExpTT.PLUS;   }
  "$"           { return RegExpTT.DOLLAR; }
  {DOT}         { return RegExpTT.DOT;    }

  "(?:"       { return RegExpTT.NON_CAPT_GROUP;  }
  "(?>"       { return RegExpTT.ATOMIC_GROUP;  }
  "(?="       { return RegExpTT.POS_LOOKAHEAD;   }
  "(?!"       { return RegExpTT.NEG_LOOKAHEAD;   }
  "(?<="      { return RegExpTT.POS_LOOKBEHIND;  }
  "(?<!"      { return RegExpTT.NEG_LOOKBEHIND;  }
  "(?#" [^)]+ ")" { return RegExpTT.COMMENT;    }
  "(?P<" { yybegin(NAMED_GROUP); capturingGroupCount++; return RegExpTT.PYTHON_NAMED_GROUP; }
  "(?P=" { yybegin(PY_NAMED_GROUP_REF); return RegExpTT.PYTHON_NAMED_GROUP_REF; }
  "(?("  { yybegin(PY_COND_REF); return RegExpTT.PYTHON_COND_REF; }

  "(?<" { yybegin(NAMED_GROUP); capturingGroupCount++; return RegExpTT.RUBY_NAMED_GROUP; }
  "(?'" { yybegin(QUOTED_NAMED_GROUP); capturingGroupCount++; return RegExpTT.RUBY_QUOTED_NAMED_GROUP; }

  "(?"        { yybegin(OPTIONS); return RegExpTT.SET_OPTIONS; }
}

<OPTIONS> {
  [:letter:]+         { handleOptions(); return RegExpTT.OPTIONS_ON; }
  ("-" [:letter:]*)   { handleOptions(); return RegExpTT.OPTIONS_OFF; }

  ":"               { yybegin(YYINITIAL); return RegExpTT.COLON;  }
  ")"               { yybegin(YYINITIAL); return RegExpTT.GROUP_END; }

  {ANY}             { yybegin(YYINITIAL); return RegExpTT.BAD_CHARACTER; }
}

<NAMED_GROUP> {
  {GROUP_NAME}      { return RegExpTT.NAME; }
  ">"               { yybegin(YYINITIAL); return RegExpTT.GT; }
  {ANY}             { yybegin(YYINITIAL); return RegExpTT.BAD_CHARACTER; }
}

<QUOTED_NAMED_GROUP> {
  {GROUP_NAME}      { return RegExpTT.NAME; }
  "'"               { yybegin(YYINITIAL); return RegExpTT.QUOTE; }
  {ANY}             { yybegin(YYINITIAL); return RegExpTT.BAD_CHARACTER; }
}

<PY_NAMED_GROUP_REF> {
  {GROUP_NAME}      { return RegExpTT.NAME;   }
  ")"               { yybegin(YYINITIAL); return RegExpTT.GROUP_END; }
  {ANY}             { yybegin(YYINITIAL); return RegExpTT.BAD_CHARACTER; }
}

<PY_COND_REF> {
  {GROUP_NAME}      { return RegExpTT.NAME; }
  [:digit:]+        { return RegExpTT.NUMBER; }
  ")"               { yybegin(YYINITIAL); return RegExpTT.GROUP_END; }
  {ANY}             { yybegin(YYINITIAL); return RegExpTT.BAD_CHARACTER; }
}

"^"                   { return RegExpTT.CARET; }
"-"                   { return RegExpTT.CHARACTER; }

/* "dangling ]" */
<YYINITIAL> {RBRACKET}    { return allowDanglingMetacharacters == Boolean.FALSE ? RegExpTT.CLASS_END : RegExpTT.CHARACTER; }


"#"           { if (commentMode) { yypushstate(COMMENT); return RegExpTT.COMMENT; } else return RegExpTT.CHARACTER; }
<COMMENT> {
  [^\r\n]*[\r\n]?  { yypopstate(); return RegExpTT.COMMENT; }
}

" "            { return commentMode ? com.intellij.psi.TokenType.WHITE_SPACE : RegExpTT.CHARACTER; }
[\n\b\t\r\f]   { return commentMode ? com.intellij.psi.TokenType.WHITE_SPACE : RegExpTT.CTRL_CHARACTER; }

{ANY}        { return RegExpTT.CHARACTER; }
