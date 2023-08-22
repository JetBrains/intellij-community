package com.siyeh.ipp.bool.demorgans;

class NeedsMoreParentheses {
  void foo(boolean a, boolean b, boolean c, boolean d) {
    boolean f = !(!(a || b) |<caret>| !(c || d));
  }
}