// "Add 'default' branch to the 'switch' statement which initializes 'j'" "false"
class X {
  void test(int i) {
    int j;
    switch(i) {
      case 0:j = 1;break;
      case 2:j = 100;break;
      default:
    }
    System.out.println(<caret>j);
  }
}