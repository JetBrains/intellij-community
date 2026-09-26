// "Replace with 'x != 0'" "true"
class X {
  public void test(int x, boolean b) {
    if(b || x < 0 |<caret>| x > 0) {}
  }
}