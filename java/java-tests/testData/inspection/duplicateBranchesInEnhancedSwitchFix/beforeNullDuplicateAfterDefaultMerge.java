// "Merge with 'default'" "true"
class Test {
  void foo(Object o) {
    switch (o) {
      default -> System.out.println("hello");
      case null -> System.out.println<caret>("hello");
    }
  }
}
