// "Create 'default' branch" "true"
class X {
  void test(int i, int j) {
    switch(i=j) {
      case 0:<caret>break;
    }
  }
}