class Foo {

  interface MyPredicate {
    boolean apply(int param);
  }

  int foo(int param1, int param2) {
    return 0;
  }

  int boo(Foo that) {
    return 0;
  }

  void zoo(MyPredicate predicate) {
  }

  int testOnFinishedStatements() {
    foo(5, 7/*typehere*/;);
    foo(1, foo(2, 3/*typehere*/;));
    int x = foo(3, 4/*typehere*/;);
    int y = (foo(3, 4/*typehere*/;));
    int z = (foo(3, 4) + boo(this/*typehere*/;));
    zoo(val->true/*typehere*/;);
    zoo(val->{ return (true/*typehere*/;); });
    zoo(val->{ return (true); }/*typehere*/;);
    testOnFinishedStatements(/*typehere*/;);
    return 0;
  }

  int testOnUnfinishedStatements() {
    foo(5/*typehere*/;, 7)
    foo(1, /*typehere*/;foo(2, 3))
    int x = foo(/*typehere*/;3, 4)
    int y = /*typehere*/;(foo(3, 4))
    int z = (foo(3, 4) + boo(/*typehere*/;this));
    zoo(val->/*typehere*/;true)
    zoo(val->{
      int x = 1;
      foo(x, x/*typehere*/;);
    })
    return 0;
  }

  void testWithFirstLoopSemicolon() {
    for (int x = 0/*typehere*/;)
  }

  void testWithSecondLoopSemicolon() {
    for (int x = 0; x <= 10/*typehere*/;)
  }

  void testWithTryWithResources() {
    try (int x = 1/*typehere*/;)
  }
}