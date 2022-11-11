// "Merge with 'default'" "true"
class Test {
  void foo(String o) {
    switch (o) {
      default:
        System.out.println(42);
        break;
      case null:
      case "hello":
        System<caret>.out.println(42);
    }
  }
}
