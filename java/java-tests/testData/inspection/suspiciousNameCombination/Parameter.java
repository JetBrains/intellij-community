public class Parameter {
  public void test() {
    int startX = 0;
    method(<warning descr="'startX' should probably not be passed as parameter 'y'">startX</warning>);
  }

  private void method(int y) {
  }
}
