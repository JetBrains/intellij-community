class Test {
  void foo(E... e) {
  }

  void bar() {
    foo(<selection>E.E1, E.E2</selection>);
  }
}

static enum E {
  E1, E2;
}