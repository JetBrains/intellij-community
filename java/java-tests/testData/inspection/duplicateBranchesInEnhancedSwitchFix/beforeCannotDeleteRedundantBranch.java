// "Delete redundant 'switch' branch" "false"
class Test {
  void foo(Stirng s) {
    switch (s) {
      case null -> System.out.println(<caret>42);
      default -> System.out.println(42);
    }
  }
}
