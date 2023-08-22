package com.siyeh.ipp.trivialif.convert_to_nested_if;

class Nested {
  boolean foo(boolean a, boolean b, boolean c) {
    return a<caret> || b ^ c && a;
  }
}