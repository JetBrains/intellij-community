// "Create 'default' branch" "true"
class X {
  void test(int i) {
    switch(i) {
      case 0 -> System.out.println("oops");
        default -> throw new IllegalStateException("Unexpected value: " + i);
    }
  }
}