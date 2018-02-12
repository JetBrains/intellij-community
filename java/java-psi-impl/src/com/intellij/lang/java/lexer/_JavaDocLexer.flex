  /* It's an automatically generated code. Do not modify it. */
package com.intellij.lang.java.lexer;

import com.intellij.lexer.DocCommentTokenTypes;
import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

@SuppressWarnings("ALL")
%%

%{
  private boolean myJdk15Enabled;
  private DocCommentTokenTypes myTokenTypes;

  public _JavaDocLexer(boolean isJdk15Enabled, DocCommentTokenTypes tokenTypes) {
    this((java.io.Reader)null);
    myJdk15Enabled = isJdk15Enabled;
    myTokenTypes = tokenTypes;
  }

  public boolean checkAhead(char c) {
    if (zzMarkedPos >= zzBuffer.length()) return false;
    return zzBuffer.charAt(zzMarkedPos) == c;
  }

  public void goTo(int offset) {
    zzCurrentPos = zzMarkedPos = zzStartRead = offset;
    zzAtEOF = false;
  }
%}

%class _JavaDocLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType

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

WHITE_DOC_SPACE_CHAR=[\ \t\f\n\r]
WHITE_DOC_SPACE_NO_LR=[\ \t\f]
DIGIT=[0-9]
ALPHA=[:jletter:]
IDENTIFIER={ALPHA}({ALPHA}|{DIGIT}|[":.-"])*
TAG_IDENTIFIER=[^\ \t\f\n\r]+
INLINE_TAG_IDENTIFIER=[^\ \t\f\n\r\}]+

%%

<YYINITIAL> "/**" { yybegin(COMMENT_DATA_START); return myTokenTypes.commentStart(); }
<COMMENT_DATA_START> {WHITE_DOC_SPACE_CHAR}+ { return myTokenTypes.space(); }
<COMMENT_DATA> {WHITE_DOC_SPACE_NO_LR}+ { return myTokenTypes.commentData(); }
<COMMENT_DATA> [\n\r]+{WHITE_DOC_SPACE_CHAR}* { return myTokenTypes.space(); }

<DOC_TAG_VALUE> {WHITE_DOC_SPACE_CHAR}+ { yybegin(COMMENT_DATA); return myTokenTypes.space(); }
<DOC_TAG_VALUE, DOC_TAG_VALUE_IN_PAREN> ({ALPHA}|[_0-9\."$"\[\]])+ { return myTokenTypes.tagValueToken(); }
<DOC_TAG_VALUE> [\(] { yybegin(DOC_TAG_VALUE_IN_PAREN); return myTokenTypes.tagValueLParen(); }
<DOC_TAG_VALUE_IN_PAREN> [\)] { yybegin(DOC_TAG_VALUE); return myTokenTypes.tagValueRParen(); }
<DOC_TAG_VALUE> [#] { return myTokenTypes.tagValueSharp(); }
<DOC_TAG_VALUE, DOC_TAG_VALUE_IN_PAREN> [,] { return myTokenTypes.tagValueComma(); }
<DOC_TAG_VALUE_IN_PAREN> {WHITE_DOC_SPACE_CHAR}+ { return myTokenTypes.space(); }

<INLINE_TAG_NAME, COMMENT_DATA_START> "@param" { yybegin(PARAM_TAG_SPACE); return myTokenTypes.tagName(); }
<PARAM_TAG_SPACE> {WHITE_DOC_SPACE_CHAR}+ {yybegin(DOC_TAG_VALUE); return myTokenTypes.space(); }
<DOC_TAG_VALUE> [\<] {
  if (myJdk15Enabled) {
    yybegin(DOC_TAG_VALUE_IN_LTGT);
    return myTokenTypes.tagValueLT();
  }
  else {
    yybegin(COMMENT_DATA);
    return myTokenTypes.commentData();
  }
}
<DOC_TAG_VALUE_IN_LTGT> {IDENTIFIER} { return myTokenTypes.tagValueToken(); }
<DOC_TAG_VALUE_IN_LTGT> {IDENTIFIER} { return myTokenTypes.tagValueToken(); }
<DOC_TAG_VALUE_IN_LTGT> [\>] { yybegin(COMMENT_DATA); return myTokenTypes.tagValueGT(); }

<COMMENT_DATA_START, COMMENT_DATA, CODE_TAG> "{" {
  if (checkAhead('@')) {
    yybegin(INLINE_TAG_NAME);
    return myTokenTypes.inlineTagStart();
  }
  else {
    yybegin(COMMENT_DATA);
    return myTokenTypes.inlineTagStart();
  }
}

<INLINE_TAG_NAME> "@code" { yybegin(CODE_TAG_SPACE); return myTokenTypes.tagName(); }
<INLINE_TAG_NAME> "@literal" { yybegin(CODE_TAG_SPACE); return myTokenTypes.tagName(); }
<INLINE_TAG_NAME> "@"{INLINE_TAG_IDENTIFIER} { yybegin(TAG_DOC_SPACE); return myTokenTypes.tagName(); }
<COMMENT_DATA_START, COMMENT_DATA, TAG_DOC_SPACE, DOC_TAG_VALUE, CODE_TAG, CODE_TAG_SPACE> "}" { yybegin(COMMENT_DATA); return myTokenTypes.inlineTagEnd(); }

<COMMENT_DATA_START, COMMENT_DATA, DOC_TAG_VALUE> . { yybegin(COMMENT_DATA); return myTokenTypes.commentData(); }
<CODE_TAG, CODE_TAG_SPACE> . { yybegin(CODE_TAG); return myTokenTypes.commentData(); }
<COMMENT_DATA_START> "@"{TAG_IDENTIFIER} { yybegin(TAG_DOC_SPACE); return myTokenTypes.tagName(); }

<TAG_DOC_SPACE> {WHITE_DOC_SPACE_CHAR}+ {
  if (checkAhead('<') || checkAhead('\"')) yybegin(COMMENT_DATA);
  else if (checkAhead('\u007b')) yybegin(COMMENT_DATA);  // lbrace - there's a error in JLex when typing lbrace directly
  else yybegin(DOC_TAG_VALUE);
  return myTokenTypes.space();
}

<CODE_TAG, CODE_TAG_SPACE> {WHITE_DOC_SPACE_CHAR}+ { yybegin(CODE_TAG); return myTokenTypes.space(); }

"*"+"/" { return myTokenTypes.commentEnd(); }
[^] { return myTokenTypes.badCharacter(); }
