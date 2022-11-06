// "Delete redundant 'switch' branch" "false"
class Test {
  void foo(Object o) {
    switch (o) {
      default:
        System.out.println(42);
        break;
      case null:
        System.out.println(42)<caret>;
    }
  }
}
