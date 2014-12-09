// "Create field 'field'" "true"
class A {
  String field;

  static class Foo {
      private final Object field<caret>;

      Foo() {
          field;
      }
  }

}

