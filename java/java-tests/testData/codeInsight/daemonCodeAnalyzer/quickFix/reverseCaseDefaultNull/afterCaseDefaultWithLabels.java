// "Replace 'case default, null' with 'case null, default'" "true-preview"
class Test {
  void f() {
    Object o = null;
    switch (o) {
      case String s:
        System.out.println("2");
          /*some text*/
        case null, default:
        System.out.println("3") /*some text2*/;
    }
}