class Test {

  Test foo(long l) {
    return this;
  }

  {
    Test t = new Test()
      .foo(-(5<caret>L))
      .foo(7L)
      .foo(-(5L));
  }
}
