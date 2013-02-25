public class Foo {
  
  Zoo foo() {
    return r<caret>
  }

  void rMethod() {}
}

enum Zoo {
  LEFT, RIGHT
}