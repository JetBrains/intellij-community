// "Merge with 'case "hello"'" "true"
class Test {
  void foo(String o) {
    switch (o) {
      case "hello":
      case null:
        System.out.println(42);
        break;
      default:
        S<caret>ystem.out.println(42);
        break;
    }
  }
}
