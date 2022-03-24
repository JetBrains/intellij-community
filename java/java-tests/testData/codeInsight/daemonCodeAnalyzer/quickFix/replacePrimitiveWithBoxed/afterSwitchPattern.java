// "Replace 'int' with 'java.lang.Integer'" "true"
class Test {
  void foo(Object o) {
    switch (o) {
      case Integer i -> System.out.println("int");
      default -> System.out.println("default");
    }
  }
}