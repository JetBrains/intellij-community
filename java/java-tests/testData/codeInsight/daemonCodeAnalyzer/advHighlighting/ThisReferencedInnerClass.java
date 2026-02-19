class Outer {
  Outer(int i) {}
  int hello() {
    return 1;
  }

  class Inner extends Outer {
    Inner() {
      super(<error descr="Cannot call 'Outer.hello()' before superclass constructor is called">hello</error>());
    }
  }
}
class Outer2 {
  Outer2(int i) {}
  private int hello() {
    return 1;
  }

  class Inner extends Outer2 {
    Inner() {
      super(hello());
    }
  }
}