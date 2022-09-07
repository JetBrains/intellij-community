// "Merge with 'default'" "true"
class Test {
  void foo(Object o) {
    switch (o) {
      default:
        System.out.println(42);
        break;
      case null:
        Syste<caret>m.out.println(42);
    }
  }
}
