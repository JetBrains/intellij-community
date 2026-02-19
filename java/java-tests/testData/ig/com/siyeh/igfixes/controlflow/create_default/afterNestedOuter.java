// "Create 'default' branch" "true"
class X {
  void test(int i, int j) {
    switch(i) {
      case 0:
          
        switch (j) {
          default: break;
        }
          break;
        default:
            throw new IllegalStateException("Unexpected value: " + i);
    }
  }
}