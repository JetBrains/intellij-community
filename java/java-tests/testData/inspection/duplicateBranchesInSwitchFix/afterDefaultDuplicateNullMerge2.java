// "Merge with 'case "hello"'" "true"
class Test {
  void foo(String o) {
    switch (o) {
      case "hello":
      case null, default:
        System.out.println(42);
        break;
    }
  }
}
