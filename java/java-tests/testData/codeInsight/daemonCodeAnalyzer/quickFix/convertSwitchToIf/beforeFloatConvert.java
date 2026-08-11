// "Replace 'switch' with 'if'" "true-preview"
class Test {
  void foo(float f) {
    switch (f<caret>) {
      case 1.5f -> System.out.println("one and half");
      case 2.5f -> System.out.println("two and half");
    }
  }
}
