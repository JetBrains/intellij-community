// "Replace implements with static import" "false"
public class X implements <caret>I {
  void bar() {
    System.out.println(FOO);
  }
}

interface I extends I1{
  String FOO = "foo";
}

interface I1 {
  void foo(){}
}