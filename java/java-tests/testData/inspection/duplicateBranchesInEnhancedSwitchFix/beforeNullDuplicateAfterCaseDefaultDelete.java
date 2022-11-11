// "Delete redundant 'switch' branch" "false"
class Test {
  void foo(String s) {
    switch (s) {
      case default -> System.out.println("hello");
      case "hello", null, "42" -> System.out<caret>.println("hello");
    }
  }
}
