class Foo0  {
  void mm() {
    Boo0 b = new Boo0();
    b.mm();
  }
}

class Foo1 extends Foo0 {
  void mm() {
    Bar0 b = new Bar0();
    b.mm();
  }
}