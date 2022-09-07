// "Merge with 'case null'" "true"
class Test {
  void foo(Object o) {
    switch (o) {
      case null -> System.out.println("hello");
      default -> System.out.println(<caret>"hello");
    }
  }
}
