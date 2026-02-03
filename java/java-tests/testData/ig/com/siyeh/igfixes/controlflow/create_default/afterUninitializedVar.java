// "Add 'default' branch to the 'switch' statement which initializes 'j'" "true"
class X {
  void test(int i) {
    int j;
    switch(i) {
      case 0:j = 1;break;
      case 2:j = 100;
          break;
        default:
            throw new IllegalStateException("Unexpected value: " + i);
    }
    System.out.println(j);
  }
}