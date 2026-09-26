// "Create 'default' branch" "true"
class X {
  void test(int i) {
    switch(i) {
      case 0:break;
        default:
            throw new IllegalStateException("Unexpected value: " + i);
    }
  }
}