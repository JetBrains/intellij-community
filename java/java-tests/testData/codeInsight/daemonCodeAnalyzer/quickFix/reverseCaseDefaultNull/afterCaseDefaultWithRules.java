// "Replace 'case default, null' with 'case null, default'" "true-preview"
class Test {
  void f() {
    Object o = null;
    switch (o) {
        /*some text*/
        case null, default -> {
            System.out.println("1") /*some text2*/;
        }
    }  }
}