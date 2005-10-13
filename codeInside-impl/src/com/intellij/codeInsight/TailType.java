
package com.intellij.codeInsight;

public interface TailType {
  int UNKNOWN = -1;
  int NONE = 0;
  int SEMICOLON = 1;
  int COMMA = 2;
  int SPACE = 3;
  int DOT = 4;
  int CAST_RPARENTH = 5;
  int CALL_RPARENTH = 6;
  int IF_RPARENTH = 7;
  int WHILE_RPARENTH = 8;
  int CALL_RPARENTH_SEMICOLON = 9;
  int CASE_COLON = 10;
  int COND_EXPR_COLON = 11;
  int EQ = 12;
  int LPARENTH = 13;
}