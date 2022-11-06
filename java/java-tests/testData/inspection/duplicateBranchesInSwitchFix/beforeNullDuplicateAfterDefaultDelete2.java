// "Delete redundant 'switch' branch" "false"
class Test {
  void foo(String o) {
    switch (o) {
      default:
        System.out.println(42);
        break;
      case null:
      case "hello":
        System.out.println(42<caret>);
    }
  }
}
