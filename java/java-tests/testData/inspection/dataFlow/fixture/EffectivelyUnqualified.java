abstract class Foo {
  protected String bar;
}

class FF extends Foo {
  class FFI extends Foo {
    void f() {
      if (this.bar == FF.this.bar);
      if (<warning descr="Condition 'this.bar == FFI.this.bar' is always 'true'">this.bar == FFI.this.bar</warning>);
    }
  }
}