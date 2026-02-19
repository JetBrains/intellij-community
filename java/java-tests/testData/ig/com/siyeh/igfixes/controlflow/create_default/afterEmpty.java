// "Create 'default' branch" "true"
class X {
  void test(int i) {
    switch(i) {
        default -> throw new IllegalStateException("Unexpected value: " + i);
    }
  }
}