public class Assignment {
  public void test() {
    int x = 0, y = 0;
    <warning descr="'x' should probably not be assigned to 'y'">y</warning> = x;
  }
}
