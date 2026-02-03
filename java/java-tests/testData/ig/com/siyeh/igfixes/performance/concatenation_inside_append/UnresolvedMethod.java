package com.siyeh.igfixes.performance.concatenation_inside_append;

class UnresolvedMethod {

  void foo(Object o) {
    StringBuilder sb = new StringBuilder();
    sb.append<caret>("asdf" + " " + o.bla() + "asdf");
    final String s = sb.toString();
  }
}