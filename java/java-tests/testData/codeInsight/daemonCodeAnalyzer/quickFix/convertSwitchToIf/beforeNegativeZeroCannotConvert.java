// "Replace 'switch' with 'if'" "false"
class Test {
  void foo(float f) {
    switch (f<caret>) {
      case -0.0f -> System.out.println("negative zero");
      case 1.5f -> System.out.println("one and half");
    }
  }
}
