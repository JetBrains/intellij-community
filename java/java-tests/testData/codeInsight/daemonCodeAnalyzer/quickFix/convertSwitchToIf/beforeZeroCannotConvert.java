// "Replace 'switch' with 'if'" "false"
class Test {
  void foo(double d) {
    switch (d<caret>) {
      case 0.0 -> System.out.println("zero");
      case 1.5 -> System.out.println("one and half");
    }
  }
}
