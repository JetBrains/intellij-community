// "Delete redundant 'switch' branch" "false"
class Test {
  void foo(String o) {
    switch (o) {
      case "hello":
      case null:
        System.out.println(42<caret>);
        break;
      default:
        System.out.println(42);
        break;
    }
  }
}
