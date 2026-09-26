// "Replace 'case default, null' with 'case null, default'" "true-preview"
class Test {
  void f() {
    Object o = null;
    switch (o) {
      case /*some text*/ defau<caret>lt, null -> {
        System.out.println("1") /*some text2*/;
      }
    }  }
}