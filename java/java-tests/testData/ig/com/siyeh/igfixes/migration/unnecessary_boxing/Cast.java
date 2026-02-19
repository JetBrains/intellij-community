package com.siyeh.igfixes.migration.unnecessary_boxing;

class Cast {
  Double foo(String s) {
    return new Do<caret>uble(s.isEmpty() ? 1 : 2);
  }
}