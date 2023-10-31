// "Replace 'case default' with 'default'" "true-preview"
class Test {
  void f() {
    Object o = null;
    int o1 = 1;
    switch (o1) {
      case 1 -> System.out.println("2");
        /*some text*/
        default -> System.out.println("3") /*some text2*/;
    }
  }
}