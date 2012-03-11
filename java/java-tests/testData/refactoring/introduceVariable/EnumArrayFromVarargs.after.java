class Test {
  void foo(E... e) {
  }

  void bar() {
      E[] strs = {E.E1, E.E2};
      foo(strs);
  }
}

static enum E {
  E1, E2;
}