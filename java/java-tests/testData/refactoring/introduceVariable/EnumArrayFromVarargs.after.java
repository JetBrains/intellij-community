class Test {
  void foo(E... e) {
  }

  void bar() {
      E[] strings = {E.E1, E.E2};
      foo(strings);
  }
}

static enum E {
  E1, E2;
}