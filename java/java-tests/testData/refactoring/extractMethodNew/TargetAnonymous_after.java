public class Foo {
  public void update() {}
}

class FooBar {
  {
    Foo tm = new Foo() {
      {
        newMethod();
      }

        private void newMethod() {
            update();
        }
    };
  }
}
