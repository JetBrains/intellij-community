package com.siyeh.ipp.switchtoif.replace_if_with_switch;

public class Test {
  String test(Test object) {
    i<caret>f (object instanceof Test) return "bar";
    return "foo";
  }
}