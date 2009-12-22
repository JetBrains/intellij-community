/* It's an automatically generated code. Do not modify it. */
package com.intellij.ide.fileTemplates.impl;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

%%

%{
   public FileTemplateTextLexer() {
     this((java.io.Reader)null);
   }
%}

%unicode
%class FileTemplateTextLexer
%implements FlexLexer
%function advance
%type IElementType
%eof{  return;
%eof}

ALPHA=[A-Za-z_]
DIGIT=[0-9]
MACRO="$"({ALPHA}|{DIGIT})+|"$""{"({ALPHA}|{DIGIT})+"}"
DIRECTIVE="#"{ALPHA}+

%%

<YYINITIAL> {MACRO} { return FileTemplateTokenType.MACRO; }
<YYINITIAL> {DIRECTIVE} { return FileTemplateTokenType.DIRECTIVE; }
<YYINITIAL> [^] { return FileTemplateTokenType.TEXT; }
