// "Replace Implements with Static Import" "true"
public class X implements <caret>I {
  void foo() {
    System.out.println(FOO);
  }
}

interface I {
  String FOO = "foo";
}