// "Merge with 'default'" "true"
class Test {
  void foo(Object o) {
    switch (o) {
        case null:
        default:
        System.out.println(42);
        break;
    }
  }
}
