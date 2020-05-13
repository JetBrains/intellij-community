class Bar {
  int method(boolean b) {
    if (b) {
      return methodTrue();
    }

    return methodFalse();
  }

  int methodTrue() {
    return 3;
  }

  int methodFalse() {
    return 5;
  }
}

class Foo {
  // IDEA-229317
  void method() {
    System.out.println(new Bar().<caret>method(true));
    System.out.println(new Bar().method(false));
    System.out.println(new Bar().method(Math.random() > 0.5));
  }
}