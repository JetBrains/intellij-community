package com.siyeh.ipp.parentheses;

class StringParentheses {

  void foo() {
    String s = "asdf" + (1 + 2 + "asdf"<caret>);
  }
}