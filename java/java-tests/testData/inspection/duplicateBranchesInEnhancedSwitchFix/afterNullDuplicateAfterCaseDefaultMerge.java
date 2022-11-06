// "Merge with 'case default'" "true"
class Test {
  void foo(String s) {
    switch (s) {
      case default, "hello", null, "42" -> System.out.println("hello");
    }
  }
}
