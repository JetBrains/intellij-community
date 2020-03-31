// "Put arguments on separate lines" "false"

class A {
  void foo(String s, String s1) {
    foo("a", // !!!<caret>
        "b");
  }
}