// "Replace 'case default' with 'default'" "true-preview"
class Test {
  void f() {
    Object o = null;
    switch (o) {
      case /*some text*/ defau<caret>lt -> {
        System.out.println("1") /*some text2*/;
      }
    }  }
}