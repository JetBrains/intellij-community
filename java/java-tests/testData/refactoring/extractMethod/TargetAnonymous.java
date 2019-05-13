public class Foo {
  public void update() {}
}

class FooBar {
  {
    Foo tm = new Foo() {
      {
        <selection>update()</selection>;
      }
    };
  }
}
