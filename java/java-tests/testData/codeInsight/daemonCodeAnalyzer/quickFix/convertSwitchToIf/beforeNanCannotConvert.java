// "Replace 'switch' with 'if'" "false"
class Test {
  void foo(double d) {
    switch (d<caret>) {
      case Double.NaN -> System.out.println("nan");
      case 1.5 -> System.out.println("one and half");
    }
  }
}
