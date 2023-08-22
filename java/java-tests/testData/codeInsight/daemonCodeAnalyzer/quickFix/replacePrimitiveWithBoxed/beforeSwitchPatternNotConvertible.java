// "Replace 'int' with 'java.lang.Integer'" "false"
class Test {
  void foo(String o) {
    switch (o) {
      case int i<caret> -> System.out.println("int");
        default -> System.out.println("default");
    }
  }
}