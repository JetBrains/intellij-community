// "Replace 'case default' with 'default'" "true-preview"
class Test {
  void f() {
    Object o = null;
    switch (o) {
        /*some text*/
        default -> {
            System.out.println("1") /*some text2*/;
        }
    }  }
}