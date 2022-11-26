// "Merge with 'case null'" "true"
class Test {
  void foo(Object o) {
    switch (o) {
      case null, default -> System.out.println("hello");
    }
  }
}
