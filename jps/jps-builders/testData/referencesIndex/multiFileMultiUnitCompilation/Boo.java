class Boo0 extends Foo1 {
  void mm() {
    Foo1 f = new Foo1();
    f.mm();
  }
}

class Boo1 extends Bar0 {
  void mm() {
    Bar1 b = new Bar1();
    b.mm();
  }
}