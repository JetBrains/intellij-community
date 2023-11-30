package com.siyeh.igfixes.performance.concatenation_inside_append;

class UnresolvedMethod {

  void foo(Object o) {
    StringBuilder sb = new StringBuilder();
    sb.append("asdf" + " ").append(o.bla()).append("asdf");
    final String s = sb.toString();
  }
}