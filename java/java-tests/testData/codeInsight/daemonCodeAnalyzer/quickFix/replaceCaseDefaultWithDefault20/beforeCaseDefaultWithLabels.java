// "Replace 'case default' with 'default'" "true-preview"
class Test {
  void f() {
    int o = 1;
    switch (o) {
      case 1:
        System.out.println("2");
      case /*some text*/ de<caret>fault:
        System.out.println("3") /*some text2*/;
    }
}