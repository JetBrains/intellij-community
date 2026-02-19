// "Replace 'int' with 'java.lang.Integer'" "true-preview"
class Test {
  void foo(Object o) {
    switch (o) {
      case int i<caret> -> System.out.println("int");
      default -> System.out.println("default");
    }
  }
}