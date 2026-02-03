public class Foo {
    void m() {
        new MyClass()<caret>
    }
  
    private class MyClass {
      public void parents() {}
    }
}
