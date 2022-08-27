public class ForStaticNestedNew {

  class Scratch {
    public static void main(String[] args) {
        new Bar(<caret>)
    }
  }

  class Foo {
    class Bar {
    }
  }
}