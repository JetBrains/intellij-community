// "Remove switch label" "true-preview"
class X {
  void test(Object obj) {
    switch (obj) {
        default:
        System.out.println("something else");
        break;
    }
  }
}