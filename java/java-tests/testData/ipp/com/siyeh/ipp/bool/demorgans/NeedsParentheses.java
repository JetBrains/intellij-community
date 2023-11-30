package com.siyeh.ipp.bool.demorgans;

class NeedsParentheses {

  void foo(boolean a, boolean b) {
    if (!(!a || !b) <caret>//between operand
        || !(a || //inside nested
             b)){}
  }
}