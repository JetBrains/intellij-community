// "Replace 'case default, null' with 'case null, default'" "true-preview"
class Test {
  void f() {
    Object o = null;
    switch (o) {
      case String s:
        System.out.println("2");
      case /*some text*/ def<caret>ault, null:
        System.out.println("3") /*some text2*/;
    }
}