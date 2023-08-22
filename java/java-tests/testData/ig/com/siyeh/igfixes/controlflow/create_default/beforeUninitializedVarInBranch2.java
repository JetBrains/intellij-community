// "Add 'default' branch to the 'switch' statement which initializes 'j'" "false"
class X {
  void test(int i) {
    int j;
    switch (i) {
      case 0:
        if (Math.random() > 0.5) {
          j = 1;
        }
        break;
      case 2:
        j = 100;
        break;
    }
    System.out.println(<caret>j);
  }
}