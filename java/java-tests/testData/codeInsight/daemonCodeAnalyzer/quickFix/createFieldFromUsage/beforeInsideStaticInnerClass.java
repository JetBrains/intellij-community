// "Create field 'field'" "true"
class A {
  String field;

  static class Foo {
      Foo() {
          fie<caret>ld;
      }
  }

}

