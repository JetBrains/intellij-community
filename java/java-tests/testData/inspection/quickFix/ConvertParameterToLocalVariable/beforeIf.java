// "Convert to local" "true"
class Temp {
  public boolean flag;

  void test(int <caret>p) {
    if (flag) {
      p = 1;
    }
    else {
      p = 2;
    }
    System.out.print(p);
  }
}