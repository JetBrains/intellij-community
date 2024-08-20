// "Create 'default' branch" "true"
class X {
  void test(int i, int j) {
    switch(i=j) {
      case 0:break;
        default:
            throw new IllegalStateException("Unexpected value: " + (i=j));
    }
  }
}