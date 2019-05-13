class Bar0 extends Foo0 {
  void mm() {
    Boo1 b = new Boo1();
    b.mm();
  }
}

class Bar1 extends Boo0 {
  void mm() {
    Foo0 f = new Foo0();
    f.mm();
  }
}