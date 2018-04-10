class X {
  void method(String t) { }
}

class Y<S extends CharSequence> extends X {
  void method(S s) { }
}

class Test {
  void x(final Y<String> err) {
    err.method<error descr="Ambiguous method call: both 'Y.method(String)' and 'X.method(String)' match">("")</error>;
  }
}