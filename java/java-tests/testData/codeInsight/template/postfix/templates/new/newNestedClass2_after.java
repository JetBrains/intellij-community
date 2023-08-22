public class ForStaticNestedNew {

  class Scratch {
    public static void main(String[] args) {
      new Foo.Bar()<caret>
    }
  }

  class Foo {
    class Bar {
    }
  }
}