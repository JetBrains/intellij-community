// "Merge with 'default'" "true"
class Test {
  void foo(String o) {
    switch (o) {
        case null:
        case "hello":
        default:
        System.out.println(42);
        break;
    }
  }
}
