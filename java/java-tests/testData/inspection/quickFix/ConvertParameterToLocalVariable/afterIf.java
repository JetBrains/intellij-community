// "Convert to local variable" "true"
class Temp {
  public boolean flag;

  void test() {
      <caret>int p;
      if (flag) {
      p = 1;
    }
    else {
      p = 2;
    }
    System.out.print(p);
  }
}