// "Replace lambda with method reference" "false"
class NonStaticInner3 {
    class Foo {
      public Foo() {
      }
    }

    interface I1<X> {
        X m();
    }

    {
        I1<Foo> b2 = () -> <caret>new Foo(){};
    }
}