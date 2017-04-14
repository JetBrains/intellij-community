class SomeClass {
  void foo(Point p) {
    p.smth(JSON<caret>);
  }
}

interface Point {
  int FOO = 2;
  int JSON = 3;
  void smth(int x);
}