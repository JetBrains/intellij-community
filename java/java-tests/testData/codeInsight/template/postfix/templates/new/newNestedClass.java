public class ForStaticNestedNew {

  class Scratch {
    public static void main(String[] args) {
      Bar.new<caret>
    }
  }

  class Foo {
    class Bar {
    }
  }
}