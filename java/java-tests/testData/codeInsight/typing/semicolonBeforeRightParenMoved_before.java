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

  int test() {
    foo(5, 7/*typehere*/)
    foo(1, foo(2, 3/*typehere*/))
    int x = foo(3, 4/*typehere*/)
    int y = (foo(3, 4/*typehere*/))
    int z = (foo(3, 4) + boo(this/*typehere*/))
    zoo(val->true/*typehere*/)
    zoo(val->{ return (true/*typehere*/) })
    zoo(val->{ return (true) }/*typehere*/)
    test(/*typehere*/)
    return 0;
  }
}