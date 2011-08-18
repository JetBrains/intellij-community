// "Create Field 'field'" "true"
class A {
  String field;

  static class Foo {
      private Object field<caret>;

      Foo() {
          field;
      }
  }

}

