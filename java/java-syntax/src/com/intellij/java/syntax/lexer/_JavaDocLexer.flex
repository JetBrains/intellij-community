package com.intellij.java.syntax.lexer;

import com.intellij.java.syntax.element.JavaDocSyntaxTokenType
import com.intellij.platform.syntax.SyntaxElementType;
import com.intellij.platform.syntax.util.lexer.FlexLexer

@Suppress("ALL")
%%

%{
  private val myJdk15Enabled: Boolean
  private var mySnippetBracesLevel = 0;
  /* Enable markdown support for java 23 */
  private var myMarkdownMode = false;

  constructor(isJdk15Enabled: Boolean) {
    myJdk15Enabled = isJdk15Enabled;
  }

  /** Should be called right after a reset */
  public fun setMarkdownMode(isEnabled: Boolean) {
    myMarkdownMode = isEnabled;
  }

  public fun checkAhead(c: Char): Boolean {
    if (zzMarkedPos >= zzBuffer.length) return false;
    return zzBuffer.get(zzMarkedPos) == c;
  }

  public fun goTo(offset: Int) {
    zzCurrentPos = offset
    zzMarkedPos = offset
    zzStartRead = offset;
    zzAtEOF = false;
  }
%}

%class _JavaDocLexer
%implements FlexLexer
%unicode
%function advance
%type SyntaxElementType

%state COMMENT_DATA_START
%state COMMENT_DATA
%state TAG_DOC_SPACE
%state PARAM_TAG_SPACE
%state DOC_TAG_VALUE
%state DOC_TAG_VALUE_IN_PAREN
%state DOC_TAG_VALUE_IN_LTGT
%state INLINE_TAG_NAME
%state CODE_TAG
%state CODE_TAG_SPACE
%state SNIPPET_TAG_COMMENT_DATA_UNTIL_COLON
%state SNIPPET_TAG_BODY_DATA
%state SNIPPET_ATTRIBUTE_VALUE_DOUBLE_QUOTES
%state SNIPPET_ATTRIBUTE_VALUE_SINGLE_QUOTES

WHITE_DOC_SPACE_CHAR=[\ \t\f\n\r]
WHITE_DOC_SPACE_NO_LR=[\ \t\f]
DIGIT=[0-9]
ALPHA=[:jletter:]
IDENTIFIER_WITHOUT_SYMBOLS={ALPHA}({ALPHA}|{DIGIT})*
IDENTIFIER={ALPHA}({ALPHA}|{DIGIT}|[":.-"])*
TAG_IDENTIFIER=[^\ \t\f\n\r]+
INLINE_TAG_IDENTIFIER=[^\ \t\f\n\r\}]+

// 20 should cover most fences. JFlex doesn't allow unlimited upper bound
CODE_FENCE_BACKTICKS=(`{3, 20})
CODE_FENCE_WAVES=(\~{3, 20})
CODE_FENCE = ({CODE_FENCE_BACKTICKS}|{CODE_FENCE_WAVES})

START_COMMENT_HTML="/**"
LEADING_TOKEN_HTML=\*
LEADING_TOKEN_MARKDOWN="///"

%%

<YYINITIAL> {START_COMMENT_HTML} {
        if (myMarkdownMode) {
          return JavaDocSyntaxTokenType.DOC_COMMENT_BAD_CHARACTER;
        }
        yybegin(COMMENT_DATA_START);
        return JavaDocSyntaxTokenType.DOC_COMMENT_START;
}
<YYINITIAL> {LEADING_TOKEN_MARKDOWN} {
        if(myMarkdownMode) {
          yybegin(COMMENT_DATA_START);
          return JavaDocSyntaxTokenType.DOC_COMMENT_LEADING_ASTERISKS;
        }
        return JavaDocSyntaxTokenType.DOC_COMMENT_BAD_CHARACTER;
}

<COMMENT_DATA_START> {WHITE_DOC_SPACE_CHAR}+ { return JavaDocSyntaxTokenType.DOC_SPACE; }
<COMMENT_DATA> {WHITE_DOC_SPACE_NO_LR}+ { return JavaDocSyntaxTokenType.DOC_COMMENT_DATA; }
<COMMENT_DATA> [\n\r]+{WHITE_DOC_SPACE_CHAR}* { return JavaDocSyntaxTokenType.DOC_SPACE; }

<DOC_TAG_VALUE> {WHITE_DOC_SPACE_CHAR}+ { yybegin(COMMENT_DATA); return JavaDocSyntaxTokenType.DOC_SPACE; }
<DOC_TAG_VALUE, DOC_TAG_VALUE_IN_PAREN> ({ALPHA}|[_0-9\."$"\[\]])+ { return JavaDocSyntaxTokenType.DOC_TAG_VALUE_TOKEN; }
<DOC_TAG_VALUE> [\(] { yybegin(DOC_TAG_VALUE_IN_PAREN); return JavaDocSyntaxTokenType.DOC_TAG_VALUE_LPAREN; }
<DOC_TAG_VALUE_IN_PAREN> [\)] { yybegin(DOC_TAG_VALUE); return JavaDocSyntaxTokenType.DOC_TAG_VALUE_RPAREN; }
<DOC_TAG_VALUE> [/] { return JavaDocSyntaxTokenType.DOC_TAG_VALUE_DIV_TOKEN; }
<DOC_TAG_VALUE> [#] { return JavaDocSyntaxTokenType.DOC_TAG_VALUE_SHARP_TOKEN; }
<DOC_TAG_VALUE, DOC_TAG_VALUE_IN_PAREN> [,] { return JavaDocSyntaxTokenType.DOC_TAG_VALUE_COMMA; }
<DOC_TAG_VALUE_IN_PAREN> {WHITE_DOC_SPACE_CHAR}+ { return JavaDocSyntaxTokenType.DOC_SPACE; }

<INLINE_TAG_NAME, COMMENT_DATA_START> "@param" { yybegin(PARAM_TAG_SPACE); return JavaDocSyntaxTokenType.DOC_TAG_NAME; }
<PARAM_TAG_SPACE> {WHITE_DOC_SPACE_CHAR}+ {yybegin(DOC_TAG_VALUE); return JavaDocSyntaxTokenType.DOC_SPACE; }
<DOC_TAG_VALUE> [\<] {
  if (myJdk15Enabled) {
    yybegin(DOC_TAG_VALUE_IN_LTGT);
    return JavaDocSyntaxTokenType.DOC_TAG_VALUE_LT;
  }
  else {
    yybegin(COMMENT_DATA);
    return JavaDocSyntaxTokenType.DOC_COMMENT_DATA;
  }
}
<DOC_TAG_VALUE_IN_LTGT> {IDENTIFIER} { return JavaDocSyntaxTokenType.DOC_TAG_VALUE_TOKEN; }
<DOC_TAG_VALUE_IN_LTGT> {IDENTIFIER} { return JavaDocSyntaxTokenType.DOC_TAG_VALUE_TOKEN; }
<DOC_TAG_VALUE_IN_LTGT> [\>] { yybegin(COMMENT_DATA); return JavaDocSyntaxTokenType.DOC_TAG_VALUE_GT; }

<COMMENT_DATA_START, COMMENT_DATA> {
      {CODE_FENCE} {
        yybegin(COMMENT_DATA);
        if(myMarkdownMode) {
          return JavaDocSyntaxTokenType.DOC_CODE_FENCE;
        }
        return JavaDocSyntaxTokenType.DOC_COMMENT_DATA;
      }

      // According to the JFlex user guide, lookahead should be avoided.
      (\\\[) { yybegin(COMMENT_DATA); return JavaDocSyntaxTokenType.DOC_COMMENT_DATA; }
      (\\\]) { yybegin(COMMENT_DATA); return JavaDocSyntaxTokenType.DOC_COMMENT_DATA; }
      (\\\() { yybegin(COMMENT_DATA); return JavaDocSyntaxTokenType.DOC_COMMENT_DATA; }
      (\\\)) { yybegin(COMMENT_DATA); return JavaDocSyntaxTokenType.DOC_COMMENT_DATA; }

      [,] {
          yybegin(COMMENT_DATA);
          if(myMarkdownMode) {
            return JavaDocSyntaxTokenType.DOC_COMMA;
          }
          return JavaDocSyntaxTokenType.DOC_COMMENT_DATA;
      }

      "`" {
         yybegin(COMMENT_DATA);
          if(myMarkdownMode) {
            return JavaDocSyntaxTokenType.DOC_INLINE_CODE_FENCE;
          }
          return JavaDocSyntaxTokenType.DOC_COMMENT_DATA;
      }

      "#" {
        yybegin(COMMENT_DATA);
        if(myMarkdownMode) {
          return JavaDocSyntaxTokenType.DOC_SHARP;
        }
        return JavaDocSyntaxTokenType.DOC_COMMENT_DATA;
      }

      \[ {
        yybegin(COMMENT_DATA);
        if(myMarkdownMode) {
          return JavaDocSyntaxTokenType.DOC_LBRACKET;
        }
        return JavaDocSyntaxTokenType.DOC_COMMENT_DATA;
      }
      \] {
        yybegin(COMMENT_DATA);
        if(myMarkdownMode) {
          return JavaDocSyntaxTokenType.DOC_RBRACKET;
        }
        return JavaDocSyntaxTokenType.DOC_COMMENT_DATA;
      }
      \( {
        yybegin(COMMENT_DATA);
        if(myMarkdownMode) {
          return JavaDocSyntaxTokenType.DOC_LPAREN;
        }
        return JavaDocSyntaxTokenType.DOC_COMMENT_DATA;
      }
      \) {
        yybegin(COMMENT_DATA);
        if(myMarkdownMode) {
          return JavaDocSyntaxTokenType.DOC_RPAREN;
        }
        return JavaDocSyntaxTokenType.DOC_COMMENT_DATA;
       }
}

<COMMENT_DATA_START, COMMENT_DATA, CODE_TAG> "{" {
  if (checkAhead('@')) {
    yybegin(INLINE_TAG_NAME);
    return JavaDocSyntaxTokenType.DOC_INLINE_TAG_START;
  }
  else {
    yybegin(COMMENT_DATA);
    return JavaDocSyntaxTokenType.DOC_INLINE_TAG_START;
  }
}

<INLINE_TAG_NAME> "@code" { yybegin(CODE_TAG_SPACE); return JavaDocSyntaxTokenType.DOC_TAG_NAME; }
<INLINE_TAG_NAME> "@literal" { yybegin(CODE_TAG_SPACE); return JavaDocSyntaxTokenType.DOC_TAG_NAME; }
<INLINE_TAG_NAME> "@snippet" { yybegin(SNIPPET_TAG_COMMENT_DATA_UNTIL_COLON); return JavaDocSyntaxTokenType.DOC_TAG_NAME; }
<INLINE_TAG_NAME> "@"{INLINE_TAG_IDENTIFIER} { yybegin(TAG_DOC_SPACE); return JavaDocSyntaxTokenType.DOC_TAG_NAME; }
<COMMENT_DATA_START, COMMENT_DATA, TAG_DOC_SPACE, DOC_TAG_VALUE, CODE_TAG, CODE_TAG_SPACE, SNIPPET_ATTRIBUTE_VALUE_DOUBLE_QUOTES,
SNIPPET_ATTRIBUTE_VALUE_SINGLE_QUOTES, SNIPPET_TAG_COMMENT_DATA_UNTIL_COLON> "}" { yybegin(COMMENT_DATA); return JavaDocSyntaxTokenType.DOC_INLINE_TAG_END; }

<COMMENT_DATA_START, COMMENT_DATA, DOC_TAG_VALUE> . { yybegin(COMMENT_DATA); return JavaDocSyntaxTokenType.DOC_COMMENT_DATA; }
<CODE_TAG, CODE_TAG_SPACE> . { yybegin(CODE_TAG); return JavaDocSyntaxTokenType.DOC_COMMENT_DATA; }
<COMMENT_DATA_START> "@"{TAG_IDENTIFIER} { yybegin(TAG_DOC_SPACE); return JavaDocSyntaxTokenType.DOC_TAG_NAME; }

<SNIPPET_ATTRIBUTE_VALUE_DOUBLE_QUOTES> {
      {WHITE_DOC_SPACE_CHAR}+ { return JavaDocSyntaxTokenType.DOC_SPACE; }
      [^\n\"]+ { return JavaDocSyntaxTokenType.DOC_TAG_VALUE_TOKEN; }
      \" { yybegin(SNIPPET_TAG_COMMENT_DATA_UNTIL_COLON); return JavaDocSyntaxTokenType.DOC_TAG_VALUE_QUOTE; }
}

<SNIPPET_ATTRIBUTE_VALUE_SINGLE_QUOTES> {
      {WHITE_DOC_SPACE_CHAR}+ { return JavaDocSyntaxTokenType.DOC_SPACE; }
      [^\n']+ { return JavaDocSyntaxTokenType.DOC_TAG_VALUE_TOKEN; }
      ' { yybegin(SNIPPET_TAG_COMMENT_DATA_UNTIL_COLON); return JavaDocSyntaxTokenType.DOC_TAG_VALUE_QUOTE; }
}

<SNIPPET_TAG_COMMENT_DATA_UNTIL_COLON> {
      {IDENTIFIER_WITHOUT_SYMBOLS} { return JavaDocSyntaxTokenType.DOC_TAG_VALUE_TOKEN; }
      = { return JavaDocSyntaxTokenType.DOC_TAG_VALUE_TOKEN; }
      \" { yybegin(SNIPPET_ATTRIBUTE_VALUE_DOUBLE_QUOTES); return JavaDocSyntaxTokenType.DOC_TAG_VALUE_QUOTE; }
      ' { yybegin(SNIPPET_ATTRIBUTE_VALUE_SINGLE_QUOTES); return JavaDocSyntaxTokenType.DOC_TAG_VALUE_QUOTE; }
      : { yybegin(SNIPPET_TAG_BODY_DATA); return JavaDocSyntaxTokenType.DOC_TAG_VALUE_COLON; }

      {LEADING_TOKEN_HTML} {
        if (myMarkdownMode) {
          return JavaDocSyntaxTokenType.DOC_COMMENT_DATA;
        }
        return JavaDocSyntaxTokenType.DOC_COMMENT_LEADING_ASTERISKS;
      }
      {LEADING_TOKEN_MARKDOWN} {
        if (myMarkdownMode) {
          return JavaDocSyntaxTokenType.DOC_COMMENT_LEADING_ASTERISKS;
        }
        return JavaDocSyntaxTokenType.DOC_COMMENT_DATA;
      }

      {WHITE_DOC_SPACE_CHAR}+ { return JavaDocSyntaxTokenType.DOC_SPACE; }

      [^\*] { yybegin(SNIPPET_TAG_COMMENT_DATA_UNTIL_COLON); return JavaDocSyntaxTokenType.DOC_COMMENT_DATA; }
      [^\/\/\/] { yybegin(SNIPPET_TAG_COMMENT_DATA_UNTIL_COLON); return JavaDocSyntaxTokenType.DOC_COMMENT_DATA; }
}

<SNIPPET_TAG_BODY_DATA> {
      \} {
        if (mySnippetBracesLevel > 0) {
          mySnippetBracesLevel--;
          return JavaDocSyntaxTokenType.DOC_COMMENT_DATA;
        } else {
          yybegin(COMMENT_DATA);
          return JavaDocSyntaxTokenType.DOC_INLINE_TAG_END;
        }
      }
      \{ { mySnippetBracesLevel++; return JavaDocSyntaxTokenType.DOC_COMMENT_DATA; }
      {WHITE_DOC_SPACE_CHAR}+ { return JavaDocSyntaxTokenType.DOC_SPACE; }
      [^{}\n]+ {return JavaDocSyntaxTokenType.DOC_COMMENT_DATA; }
}

<TAG_DOC_SPACE> {WHITE_DOC_SPACE_CHAR}+ {
  if (checkAhead('<') || checkAhead('\"')) yybegin(COMMENT_DATA);
  else if (checkAhead('\u007b')) yybegin(COMMENT_DATA);  // lbrace - there's a error in JLex when typing lbrace directly
  else yybegin(DOC_TAG_VALUE);
  return JavaDocSyntaxTokenType.DOC_SPACE;
}

<CODE_TAG, CODE_TAG_SPACE> {WHITE_DOC_SPACE_CHAR}+ { yybegin(CODE_TAG); return JavaDocSyntaxTokenType.DOC_SPACE; }

"*"+"/" {
    if(myMarkdownMode) {
      return JavaDocSyntaxTokenType.DOC_COMMENT_DATA;
    }
    return JavaDocSyntaxTokenType.DOC_COMMENT_END;
}
[^] { return JavaDocSyntaxTokenType.DOC_COMMENT_BAD_CHARACTER; }
