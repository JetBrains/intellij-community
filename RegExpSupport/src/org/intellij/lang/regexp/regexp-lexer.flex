/* It's an automatically generated code. Do not modify it. */
package org.intellij.lang.regexp;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;

import java.util.ArrayList;
import java.util.EnumSet;

@SuppressWarnings("ALL")
%%

%class _RegExLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType

%{
    // This adds support for nested states. I'm no JFlex pro, so maybe this is overkill, but it works quite well.
    final ArrayList<Integer> states = new ArrayList<>();

    // This was an idea to use the regex implementation for XML schema regexes (which use a slightly different syntax)
    // as well, but is currently unfinished as it requires to tweak more places than just the lexer.
    private boolean xmlSchemaMode;

    int capturingGroupCount = 0;

    private boolean allowDanglingMetacharacters;
    private boolean allowNestedCharacterClasses;
    private boolean allowOctalNoLeadingZero;
    private boolean allowHexDigitClass;
    private boolean allowEmptyCharacterClass;
    private boolean allowHorizontalWhitespaceClass;
    private boolean allowCategoryShorthand;
    private boolean allowPosixBracketExpressions;
    private boolean allowCaretNegatedProperties;

    _RegExLexer(EnumSet<RegExpCapability> capabilities) {
      this((java.io.Reader)null);
      this.xmlSchemaMode = capabilities.contains(RegExpCapability.XML_SCHEMA_MODE);
      this.allowDanglingMetacharacters = capabilities.contains(RegExpCapability.DANGLING_METACHARACTERS);
      this.allowNestedCharacterClasses = capabilities.contains(RegExpCapability.NESTED_CHARACTER_CLASSES);
      this.allowOctalNoLeadingZero = capabilities.contains(RegExpCapability.OCTAL_NO_LEADING_ZERO);
      this.commentMode = capabilities.contains(RegExpCapability.COMMENT_MODE);
      this.allowHexDigitClass = capabilities.contains(RegExpCapability.ALLOW_HEX_DIGIT_CLASS);
      this.allowHorizontalWhitespaceClass = capabilities.contains(RegExpCapability.ALLOW_HORIZONTAL_WHITESPACE_CLASS);
      this.allowEmptyCharacterClass = capabilities.contains(RegExpCapability.ALLOW_EMPTY_CHARACTER_CLASS);
      this.allowCategoryShorthand = capabilities.contains(RegExpCapability.UNICODE_CATEGORY_SHORTHAND);
      this.allowPosixBracketExpressions = capabilities.contains(RegExpCapability.POSIX_BRACKET_EXPRESSIONS);
      this.allowCaretNegatedProperties = capabilities.contains(RegExpCapability.CARET_NEGATED_PROPERTIES);
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
%xstate CLASS1
%xstate NEGATE_CLASS1
%state CLASS2
%state PROP
%xstate OPTIONS
%xstate COMMENT
%xstate NAMED_GROUP
%xstate QUOTED_NAMED_GROUP
%xstate PY_NAMED_GROUP_REF
%xstate PY_COND_REF
%xstate BRACKET_EXPRESSION

DIGITS=[1-9][0-9]*

DOT="."
LPAREN="("
RPAREN=")"
LBRACE="{"
RBRACE="}"
LBRACKET="["
RBRACKET="]"

ESCAPE="\\"
NAME=[:letter:]([:letter:]|_|[:digit:])*
ANY=[^]

META1 = {ESCAPE} | {LBRACKET} | "^"
META2= {DOT} | "$" | "?" | "*" | "+" | "|" | {LBRACE} | {LPAREN} | {RPAREN}

CONTROL="t" | "n" | "r" | "f" | "a" | "e"
BOUNDARY="b" | "b{g}"| "B" | "A" | "z" | "Z" | "G"

CLASS="w" | "W" | "s" | "S" | "d" | "D" | "v" | "V" | "X"
XML_CLASS="c" | "C" | "i" | "I"
PROP="p" | "P"

HEX_CHAR=[0-9a-fA-F]

%%

"\\Q"                { yypushstate(QUOTED); return RegExpTT.QUOTE_BEGIN; }

<QUOTED> {
  "\\E"              { yypopstate(); return RegExpTT.QUOTE_END; }
  {ANY}              { return RegExpTT.CHARACTER; }
}

/* \\ */
{ESCAPE} {ESCAPE}    { return RegExpTT.ESC_CHARACTER; }

/* hex escapes */
{ESCAPE} "x" ({HEX_CHAR}{2}|{LBRACE}{HEX_CHAR}{1,6}{RBRACE})  { return RegExpTT.HEX_CHAR; }
{ESCAPE} "x" ({HEX_CHAR}?|{LBRACE}{HEX_CHAR}*{RBRACE}?)   { return RegExpTT.BAD_HEX_VALUE; }

/* unicode escapes */
{ESCAPE} "u" {HEX_CHAR}{4}   { return RegExpTT.UNICODE_CHAR; }
{ESCAPE} "u" {HEX_CHAR}{0,3} { return StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN; }

/* octal escapes */
{ESCAPE} "0" [0-7]{1,3}      { return RegExpTT.OCT_CHAR; }
{ESCAPE} "0"                 { return (allowOctalNoLeadingZero ? RegExpTT.OCT_CHAR : RegExpTT.BAD_OCT_VALUE); }

/* single character after "\c" */
{ESCAPE} "c" {ANY}           { if (xmlSchemaMode) { yypushback(1); return RegExpTT.CHAR_CLASS; } else return RegExpTT.CTRL; }

{ESCAPE} {XML_CLASS}         { if (xmlSchemaMode) return RegExpTT.CHAR_CLASS; else return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN; }


/* java.util.regex.Pattern says about backrefs:
    "In this class, \1 through \9 are always interpreted as back references,
    and a larger number is accepted as a back reference if at least that many
    subexpressions exist at that point in the regular expression, otherwise the
    parser will drop digits until the number is smaller or equal to the existing
    number of groups or it is one digit."
*/
{ESCAPE} [0-7]{3}             { if (allowOctalNoLeadingZero) return RegExpTT.OCT_CHAR;
                                if (yystate() == CLASS2) return RegExpTT.ESC_CHARACTER;
                                while (yylength() > 2 && Integer.parseInt(yytext().toString().substring(1)) > capturingGroupCount) {
                                  yypushback(1);
                                }
                                return RegExpTT.BACKREF;
                              }

{ESCAPE} {DIGITS}             { if (yystate() == CLASS2) return RegExpTT.ESC_CHARACTER;
                                while (yylength() > 2 && Integer.parseInt(yytext().toString().substring(1)) > capturingGroupCount) {
                                  yypushback(1);
                                }
                                return RegExpTT.BACKREF;
                              }

{ESCAPE}  "-"                 { return (yystate() == CLASS2) ? RegExpTT.ESC_CHARACTER : RegExpTT.REDUNDANT_ESCAPE; }
{ESCAPE}  {META1}             { return RegExpTT.ESC_CHARACTER; }
{ESCAPE}  {META2}             { return (yystate() == CLASS2) ? RegExpTT.REDUNDANT_ESCAPE : RegExpTT.ESC_CHARACTER; }
{ESCAPE}  {CLASS}             { return RegExpTT.CHAR_CLASS;    }
{ESCAPE}  "R"                 { return RegExpTT.CHAR_CLASS;    }
{ESCAPE}  {PROP}              { yypushstate(PROP); return RegExpTT.PROPERTY;      }

{ESCAPE}  {BOUNDARY}          { return yystate() != CLASS2 ? RegExpTT.BOUNDARY : RegExpTT.ESC_CHARACTER; }
{ESCAPE}  {CONTROL}           { return RegExpTT.ESC_CTRL_CHARACTER; }

{ESCAPE} [hH]                 { return (allowHexDigitClass || allowHorizontalWhitespaceClass ? RegExpTT.CHAR_CLASS : StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN); }
{ESCAPE} "k<"                 { yybegin(NAMED_GROUP); return RegExpTT.RUBY_NAMED_GROUP_REF; }
{ESCAPE} "k'"                 { yybegin(QUOTED_NAMED_GROUP); return RegExpTT.RUBY_QUOTED_NAMED_GROUP_REF; }
{ESCAPE} "g<"                 { yybegin(NAMED_GROUP); return RegExpTT.RUBY_NAMED_GROUP_CALL; }
{ESCAPE} "g'"                 { yybegin(QUOTED_NAMED_GROUP); return RegExpTT.RUBY_QUOTED_NAMED_GROUP_CALL; }
{ESCAPE}  [:letter:]          { return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN; }
{ESCAPE}  [\n\b\t\r\f ]       { return commentMode ? RegExpTT.CHARACTER : RegExpTT.REDUNDANT_ESCAPE; }

<CLASS2> {
  {ESCAPE} {RBRACKET}         { if (!allowNestedCharacterClasses) return RegExpTT.CHARACTER;
                                return RegExpTT.REDUNDANT_ESCAPE; }
}

{ESCAPE}  {ANY}               { return RegExpTT.REDUNDANT_ESCAPE; }


{ESCAPE}                      { return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN; }

<PROP> {
  {LBRACE}                    { yypopstate(); yypushstate(EMBRACED); return RegExpTT.LBRACE; }
  "L"|"M"|"Z"|"S"|"N"|"P"|"C" { yypopstate(); if (allowCategoryShorthand) return RegExpTT.CATEGORY_SHORT_HAND; else yypushback(1); }
  {ANY}                       { yypopstate(); yypushback(1); }
}

/* "{" \d+(,\d*)? "}" */
/* "}" outside counted closure is treated as regular character */
{LBRACE}              { if (yystate() != CLASS2) yypushstate(EMBRACED); return RegExpTT.LBRACE; }

<EMBRACED> {
  "^"                 {
                        if (allowCaretNegatedProperties) {
                          return RegExpTT.CARET;
                        } else if (allowDanglingMetacharacters) {
                          yypopstate();
                          yypushback(1);
                        } else {
                          return RegExpTT.BAD_CHARACTER;
                        }
                      }
  {NAME}              { return RegExpTT.NAME;   }
  [:digit:]+          { return RegExpTT.NUMBER; }
  ","                 { return RegExpTT.COMMA;  }

  {RBRACE}            { yypopstate(); return RegExpTT.RBRACE; }
  {ANY}               { if (allowDanglingMetacharacters) {
                          yypopstate(); yypushback(1);
                        } else {
                          return RegExpTT.BAD_CHARACTER;
                        }
                      }
}

"-"                   { return RegExpTT.MINUS; }
"^"                   { return RegExpTT.CARET; }

<CLASS2> {
  {LBRACKET}          { if (allowNestedCharacterClasses) {
                           yypushstate(CLASS2);
                           return RegExpTT.CLASS_BEGIN;
                        }
                        return RegExpTT.CHARACTER;
                      }

  {LBRACKET} / {RBRACKET} { if (allowNestedCharacterClasses) {
                              yypushstate(CLASS1);
                              return RegExpTT.CLASS_BEGIN;
                            }
                            return RegExpTT.CHARACTER;
                          }
}

{LBRACKET} / {RBRACKET}   { if (allowEmptyCharacterClass) yypushstate(CLASS2); else yypushstate(CLASS1);
                            return RegExpTT.CLASS_BEGIN; }

{LBRACKET} / "^" {RBRACKET} { if (allowEmptyCharacterClass) yypushstate(CLASS2); else yypushstate(NEGATE_CLASS1);
                              return RegExpTT.CLASS_BEGIN; }

{LBRACKET}                { yypushstate(CLASS2);
                            return RegExpTT.CLASS_BEGIN; }

/* []abc] is legal. The first ] is treated as literal character */
<CLASS1> {
  {RBRACKET}              { yybegin(CLASS2); return RegExpTT.CHARACTER; }
  .                       { assert false : yytext(); }
}

<NEGATE_CLASS1> {
  "^"                     { yybegin(CLASS1); return RegExpTT.CARET; }
  .                       { assert false : yytext(); }
}

<CLASS2> {
  {LBRACKET} ":"         { if (allowPosixBracketExpressions) {
                            yybegin(BRACKET_EXPRESSION);
                            return RegExpTT.BRACKET_EXPRESSION_BEGIN;
                          } else {
                            yypushback(1);
                            return RegExpTT.CHARACTER;
                          }
                        }
  {RBRACKET}            { yypopstate(); return RegExpTT.CLASS_END; }

  "&&"                  { if (allowNestedCharacterClasses) return RegExpTT.ANDAND; else yypushback(1); return RegExpTT.CHARACTER; }
  [\n\b\t\r\f]          { return commentMode ? com.intellij.psi.TokenType.WHITE_SPACE : RegExpTT.ESC_CHARACTER; }
  {ANY}                 { return RegExpTT.CHARACTER; }
}

<BRACKET_EXPRESSION> {
  "^"                                     { return RegExpTT.CARET; }
  {NAME}                                  { return RegExpTT.NAME;   }
  ":" {RBRACKET}                          { yybegin(CLASS2); return RegExpTT.BRACKET_EXPRESSION_END; }
  {ANY}                                   { return RegExpTT.BAD_CHARACTER; }
}

<YYINITIAL> {
  {LPAREN}      { capturingGroupCount++; return RegExpTT.GROUP_BEGIN; }
  {RPAREN}      { return RegExpTT.GROUP_END;   }

  "|"           { return RegExpTT.UNION;  }
  "?"           { return RegExpTT.QUEST;  }
  "*"           { return RegExpTT.STAR;   }
  "+"           { return RegExpTT.PLUS;   }
  "$"           { return RegExpTT.DOLLAR; }
  {DOT}         { return RegExpTT.DOT;    }

  "(?:"|"(?>" { return RegExpTT.NON_CAPT_GROUP;  }
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
  {NAME}            { return RegExpTT.NAME; }
  ">"               { yybegin(YYINITIAL); return RegExpTT.GT; }
  {ANY}             { yybegin(YYINITIAL); return RegExpTT.BAD_CHARACTER; }
}

<QUOTED_NAMED_GROUP> {
  {NAME}            { return RegExpTT.NAME; }
  "'"               { yybegin(YYINITIAL); return RegExpTT.QUOTE; }
  {ANY}             { yybegin(YYINITIAL); return RegExpTT.BAD_CHARACTER; }
}

<PY_NAMED_GROUP_REF> {
  {NAME}            { return RegExpTT.NAME;   }
  ")"               { yybegin(YYINITIAL); return RegExpTT.GROUP_END; }
  {ANY}             { yybegin(YYINITIAL); return RegExpTT.BAD_CHARACTER; }
}

<PY_COND_REF> {
  {NAME}            { return RegExpTT.NAME; }
  [:digit:]+        { return RegExpTT.NUMBER; }
  ")"               { yybegin(YYINITIAL); return RegExpTT.GROUP_END; }
  {ANY}             { yybegin(YYINITIAL); return RegExpTT.BAD_CHARACTER; }
}

/* "dangling ]" */
<YYINITIAL> {RBRACKET}    { return RegExpTT.CHARACTER; }


"#"           { if (commentMode) { yypushstate(COMMENT); return RegExpTT.COMMENT; } else return RegExpTT.CHARACTER; }
<COMMENT> {
  [^\r\n]*[\r\n]?  { yypopstate(); return RegExpTT.COMMENT; }
}

" "          { return commentMode ? com.intellij.psi.TokenType.WHITE_SPACE : RegExpTT.CHARACTER; }
[\n\b\t\r\f]   { return commentMode ? com.intellij.psi.TokenType.WHITE_SPACE : RegExpTT.CTRL_CHARACTER; }

{ANY}        { return RegExpTT.CHARACTER; }
