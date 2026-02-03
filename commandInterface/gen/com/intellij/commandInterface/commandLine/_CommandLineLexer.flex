package com.intellij.commandInterface.commandLine;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static com.intellij.commandInterface.commandLine.CommandLineElementTypes.*;

%%

%{
  public _CommandLineLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _CommandLineLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL=\R
WHITE_SPACE=\s+

SPACE=[ \t\n\x0B\f\r]+
LITERAL_STARTS_FROM_LETTER=[:letter:]([a-zA-Z_0-9]|:|\\|"/"|\.|-|\!|\*)*
LITERAL_STARTS_FROM_DIGIT=[:digit:]([a-zA-Z_0-9]|:|\\|"/"|\.|-|\!|\*)*
LITERAL_STARTS_FROM_SYMBOL=([/\~\.!*]([a-zA-Z_0-9]|:|\\|"/"|\.|-|\!|\*)*)
SPACED_LITERAL_STARTS_FROM_LETTER=\"[:letter:]([a-zA-Z_0-9]|:|\\|"/"|\.|-|[ \t\n\x0B\f\r]|\!|\*)*\"
SPACED_LITERAL_STARTS_FROM_DIGIT=\"[:digit:]([a-zA-Z_0-9]|:|\\|"/"|\.|-|[ \t\n\x0B\f\r]|\!|\*)*\"
SPACED_LITERAL_STARTS_FROM_SYMBOL=\"([/\~\.!*]([a-zA-Z_0-9]|:|\\|"/"|\.|-|[ \t\n\x0B\f\r]|\!|\*)*)\"
SINGLE_Q_SPACED_LITERAL_STARTS_FROM_LETTER='[:letter:]([a-zA-Z_0-9]|:|\\|"/"|\.|-|[ \t\n\x0B\f\r]|\!|\*)*'
SINGLE_Q_SPACED_LITERAL_STARTS_FROM_DIGIT='[:digit:]([a-zA-Z_0-9]|:|\\|"/"|\.|-|[ \t\n\x0B\f\r]|\!|\*)*'
SINGLE_Q_SPACED_LITERAL_STARTS_FROM_SYMBOL='([/\~\.!*]([a-zA-Z_0-9]|:|\\|"/"|\.|-|[ \t\n\x0B\f\r]|\!|\*)*)'
SHORT_OPTION_NAME_TOKEN=-[:letter:]
LONG_OPTION_NAME_TOKEN=--[:letter:](-|[a-zA-Z_0-9])*

%%
<YYINITIAL> {
  {WHITE_SPACE}                                      { return WHITE_SPACE; }

  "="                                                { return EQ; }

  {SPACE}                                            { return SPACE; }
  {LITERAL_STARTS_FROM_LETTER}                       { return LITERAL_STARTS_FROM_LETTER; }
  {LITERAL_STARTS_FROM_DIGIT}                        { return LITERAL_STARTS_FROM_DIGIT; }
  {LITERAL_STARTS_FROM_SYMBOL}                       { return LITERAL_STARTS_FROM_SYMBOL; }
  {SPACED_LITERAL_STARTS_FROM_LETTER}                { return SPACED_LITERAL_STARTS_FROM_LETTER; }
  {SPACED_LITERAL_STARTS_FROM_DIGIT}                 { return SPACED_LITERAL_STARTS_FROM_DIGIT; }
  {SPACED_LITERAL_STARTS_FROM_SYMBOL}                { return SPACED_LITERAL_STARTS_FROM_SYMBOL; }
  {SINGLE_Q_SPACED_LITERAL_STARTS_FROM_LETTER}       { return SINGLE_Q_SPACED_LITERAL_STARTS_FROM_LETTER; }
  {SINGLE_Q_SPACED_LITERAL_STARTS_FROM_DIGIT}        { return SINGLE_Q_SPACED_LITERAL_STARTS_FROM_DIGIT; }
  {SINGLE_Q_SPACED_LITERAL_STARTS_FROM_SYMBOL}       { return SINGLE_Q_SPACED_LITERAL_STARTS_FROM_SYMBOL; }
  {SHORT_OPTION_NAME_TOKEN}                          { return SHORT_OPTION_NAME_TOKEN; }
  {LONG_OPTION_NAME_TOKEN}                           { return LONG_OPTION_NAME_TOKEN; }

}

[^] { return BAD_CHARACTER; }
