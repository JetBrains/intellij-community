// "Delete redundant 'switch' branch" "false"
class Test {
  void foo(Object o) {
    switch (o) {
      case null -> System.out.println<caret>("hello");
      default -> System.out.println("hello");
    }
  }
}
