// "Delete redundant 'switch' branch" "false"
class Test {
  void foo(Object o) {
    switch (o) {
      case null -> System.out<caret>.println("hello");
      case default -> System.out.println("hello");
    }
  }
}
