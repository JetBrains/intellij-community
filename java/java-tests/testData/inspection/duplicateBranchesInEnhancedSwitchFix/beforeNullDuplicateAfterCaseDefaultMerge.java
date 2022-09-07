// "Merge with 'case default'" "true"
class Test {
  void foo(String s) {
    switch (s) {
      case default -> System.out.println("hello");
      case "hello", null, "42" -> System.out.println<caret>("hello");
    }
  }
}
