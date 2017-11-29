// "Replace with '(b ? null : "foo") != null ?:'" "true"
class A {
  void bar(String s) {}

  void foo(boolean b){
    bar(b ? null<caret> : "foo");
  }
}