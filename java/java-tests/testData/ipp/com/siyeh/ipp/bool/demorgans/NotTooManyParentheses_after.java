package com.siyeh.ipp.bool.demorgans;

class NotTooManyParentheses {
  void foo(boolean a, boolean b, boolean c) {
      //cooment inside expr
      if (a && !(!b && c != c)) {}
  }
}