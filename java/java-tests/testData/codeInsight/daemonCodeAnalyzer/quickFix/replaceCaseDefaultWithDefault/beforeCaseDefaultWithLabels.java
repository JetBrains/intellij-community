// "Replace 'case default' with 'default'" "true-preview"
class Test {
  void f() {
    Object o = null;
    switch (o) {
      case String s:
        System.out.println("2");
      case /*some text*/ def<caret>ault:
        System.out.println("3") /*some text2*/;
    }
}