/* It's an automatically generated code. Do not modify it. */
package org.intellij.lang.regexp;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import java.util.LinkedList;
import com.intellij.psi.StringEscapesTokenTypes;

// IDEADEV-11055
@SuppressWarnings({ "ALL", "SameParameterValue", "WeakerAccess", "SameReturnValue", "RedundantThrows", "UnusedDeclaration", "UnusedDeclaration" })
%%

%class _RegExLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{  return;
%eof}

%{
    // This adds support for nested states. I'm no JFlex pro, so maybe this is overkill, but it works quite well.
    private final LinkedList<Integer> states = new LinkedList();

    // This was an idea to use the regex implementation for XML schema regexes (which use a slightly different syntax)
    // as well, but is currently unfinished as it requires to tweak more places than just the lexer. 
    private boolean xmlSchemaMode;

    private boolean allowDanglingMetacharacters;

    _RegExLexer(boolean xmlSchemaMode, boolean allowDanglingMetacharacters) {
      this((java.io.Reader)null);
      this.xmlSchemaMode = xmlSchemaMode;
      this.allowDanglingMetacharacters = allowDanglingMetacharacters;
    }

    private void yypushstate(int state) {
        states.addFirst(yystate());
        yybegin(state);
    }
    private void yypopstate() {
        final int state = states.removeFirst();
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
%state CLASS2
%state PROP
%xstate OPTIONS
%xstate COMMENT
%xstate NAMED_GROUP
%xstate QUOTED_NAMED_GROUP
%xstate PY_NAMED_GROUP_REF
%xstate PY_COND_REF

DIGITS=[1-9][0-9]*

DOT="."
LPAREN="("
RPAREN=")"
LBRACE="{"
RBRACE="}"
LBRACKET="["
RBRACKET="]"

ESCAPE="\\"
ANY=.|\n

META={ESCAPE} | {DOT} |
  "^" | "$" | "?" | "*" | "+" | "|" |
  {LBRACKET} | {LBRACE} | {LPAREN} | {RPAREN}

CONTROL="t" | "n" | "r" | "f" | "a" | "e"
BOUNDARY="b" | "B" | "A" | "z" | "Z" | "G"

CLASS="w" | "W" | "s" | "S" | "d" | "D" | "X" | "C"
XML_CLASS="c" | "C" | "i" | "I"
PROP="p" | "P"

HEX_CHAR=[0-9a-fA-F]

%%

"\\Q"                { yypushstate(QUOTED); return RegExpTT.QUOTE_BEGIN; }

<QUOTED> {
  "\\E"              { yypopstate(); return RegExpTT.QUOTE_END; }
  .                  { return RegExpTT.CHARACTER; }
}

/* \\ */
{ESCAPE} {ESCAPE}    { return RegExpTT.ESC_CHARACTER; }

/* hex escapes */
{ESCAPE} "x" {HEX_CHAR}{2}   { return RegExpTT.HEX_CHAR; }
{ESCAPE} "x" {ANY}{0,2}      { return RegExpTT.BAD_HEX_VALUE; }

/* unicode escapes */
{ESCAPE} "u" {HEX_CHAR}{4}   { return RegExpTT.UNICODE_CHAR; }
{ESCAPE} "u" {ANY}{0,4}      { return StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN; }

/* octal escapes */
{ESCAPE} "0" [0-7]{1,3}      { return RegExpTT.OCT_CHAR; }
{ESCAPE} "0"                 { return RegExpTT.BAD_OCT_VALUE; }

/* single character after "\c" */
{ESCAPE} "c" {ANY}           { if (xmlSchemaMode) { yypushback(1); return RegExpTT.CHAR_CLASS; } else return RegExpTT.CTRL; }

{ESCAPE} {XML_CLASS}         { if (xmlSchemaMode) return RegExpTT.CHAR_CLASS; else return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN; }


/* java.util.regex.Pattern says about backrefs:
    "In this class, \1 through \9 are always interpreted as back references,
    and a larger number is accepted as a back reference if at least that many
    subexpressions exist at that point in the regular expression, otherwise the
    parser will drop digits until the number is smaller or equal to the existing
    number of groups or it is one digit."

    So, for 100% compatibility, backrefs > 9 should be resolved by the parser, but
    I'm not sure if it's worth the effort - at least not atm.
*/
      
{ESCAPE} {DIGITS}             { return yystate() != CLASS2 ? RegExpTT.BACKREF : RegExpTT.ESC_CHARACTER; }

{ESCAPE}  "-"                 { return RegExpTT.ESC_CHARACTER; }
{ESCAPE}  {META}              { return RegExpTT.ESC_CHARACTER; }
{ESCAPE}  {CLASS}             { return RegExpTT.CHAR_CLASS;    }
{ESCAPE}  {PROP}              { yypushstate(PROP); return RegExpTT.PROPERTY;      }

{ESCAPE}  {BOUNDARY}          { return yystate() != CLASS2 ? RegExpTT.BOUNDARY : RegExpTT.ESC_CHARACTER; }
{ESCAPE}  {CONTROL}           { return RegExpTT.ESC_CTRL_CHARACTER; }

{ESCAPE}  [:letter:]          { return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN; }
{ESCAPE}  [\n\b\t\r\f ]       { return commentMode ? RegExpTT.CHARACTER : RegExpTT.REDUNDANT_ESCAPE; }
{ESCAPE}  {ANY}               { return RegExpTT.REDUNDANT_ESCAPE; }
{ESCAPE}                      { return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN; }

<PROP> {
  {LBRACE}                    { yypopstate(); yypushstate(EMBRACED); return RegExpTT.LBRACE; }
  {ANY}                       { yypopstate(); yypushback(1); }
}

/* "{" \d+(,\d*)? "}" */
/* "}" outside counted closure is treated as regular character */
{LBRACE}              { if (yystate() != CLASS2) yypushstate(EMBRACED); return RegExpTT.LBRACE; }

<EMBRACED> {
  [:letter:]([:letter:]|_|[:digit:])*     { return RegExpTT.NAME;   }
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

{LBRACKET} / {RBRACKET}   { yypushstate(CLASS1); return RegExpTT.CLASS_BEGIN; }
{LBRACKET}                { yypushstate(CLASS2); return RegExpTT.CLASS_BEGIN; }

/* []abc] is legal. The first ] is treated as literal character */
<CLASS1> {
  {RBRACKET}              { yybegin(CLASS2); return RegExpTT.CHARACTER; }
  .                       { assert false : yytext(); }
}

<CLASS2> {
  {RBRACKET}            { yypopstate(); return RegExpTT.CLASS_END; }

  "&&"                  { return RegExpTT.ANDAND;    }
  [\n\b\t\r\f]          { return commentMode ? com.intellij.psi.TokenType.WHITE_SPACE : RegExpTT.ESC_CHARACTER; }
  {ANY}                 { return RegExpTT.CHARACTER; }
}

<YYINITIAL> {
  {LPAREN}      { return RegExpTT.GROUP_BEGIN; }
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
  "(?P<" { yybegin(NAMED_GROUP); return RegExpTT.PYTHON_NAMED_GROUP; }
  "(?P=" { yybegin(PY_NAMED_GROUP_REF); return RegExpTT.PYTHON_NAMED_GROUP_REF; }
  "(?("  { yybegin(PY_COND_REF); return RegExpTT.PYTHON_COND_REF; }

  "(?<" { yybegin(NAMED_GROUP); return RegExpTT.RUBY_NAMED_GROUP; }
  "(?'" { yybegin(QUOTED_NAMED_GROUP); return RegExpTT.RUBY_QUOTED_NAMED_GROUP; }

  "(?"        { yybegin(OPTIONS); return RegExpTT.SET_OPTIONS; }
}

<OPTIONS> {
  [:letter:]*         { handleOptions(); return RegExpTT.OPTIONS_ON; }
  ("-" [:letter:]*)   { handleOptions(); return RegExpTT.OPTIONS_OFF; }

  ":"               { yybegin(YYINITIAL); return RegExpTT.COLON;  }
  ")"               { yybegin(YYINITIAL); return RegExpTT.GROUP_END; }

  {ANY}             { yybegin(YYINITIAL); return RegExpTT.BAD_CHARACTER; }
}

<NAMED_GROUP> {
  [:letter:]([:letter:]|_|[:digit:])* { return RegExpTT.NAME; }
  ">"               { yybegin(YYINITIAL); return RegExpTT.GT; }
  {ANY}             { yybegin(YYINITIAL); return RegExpTT.BAD_CHARACTER; }
}

<QUOTED_NAMED_GROUP> {
  [:letter:]([:letter:]|_|[:digit:])* { return RegExpTT.NAME; }
  "'"               { yybegin(YYINITIAL); return RegExpTT.QUOTE; }
  {ANY}             { yybegin(YYINITIAL); return RegExpTT.BAD_CHARACTER; }
}

<PY_NAMED_GROUP_REF> {
  [:letter:]([:letter:]|_|[:digit:])* { return RegExpTT.NAME;   }
  ")"               { yybegin(YYINITIAL); return RegExpTT.GROUP_END; }
  {ANY}             { yybegin(YYINITIAL); return RegExpTT.BAD_CHARACTER; }
}

<PY_COND_REF> {
  [:letter:]([:letter:]|_|[:digit:])* { return RegExpTT.NAME; }
  [:digit:]+          { return RegExpTT.NUMBER; }
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
