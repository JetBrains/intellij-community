// "Delete redundant 'switch' branch" "false"
class Test {
  void foo(Object o) {
    switch (o) {
      default -> System.out.println("hello");
      case null -> System.ou<caret>t.println("hello");
    }
  }
}
