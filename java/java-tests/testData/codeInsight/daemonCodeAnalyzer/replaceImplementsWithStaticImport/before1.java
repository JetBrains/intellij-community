// "Replace implements with static import" "true-preview"
public class X implements <caret>I {
  void foo() {
    System.out.println(FOO);
  }
}

interface I {
  String FOO = "foo";
}