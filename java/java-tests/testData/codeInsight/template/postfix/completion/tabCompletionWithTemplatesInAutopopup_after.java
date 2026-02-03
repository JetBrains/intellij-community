public class Foo {
    void m() {
        new MyClass().parents();<caret>
    }
  
    private class MyClass {
      public void parents() {}
    }
}
