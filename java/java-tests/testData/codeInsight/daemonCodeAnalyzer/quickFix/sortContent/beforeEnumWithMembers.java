// "Sort content" "true"

enum e {
  Foo(1),
  Bar(2),<caret>
  Baz(5);

  int i;

  e(int i) {
    this.i = i;
  }

  void doSomething() {

  }
}
