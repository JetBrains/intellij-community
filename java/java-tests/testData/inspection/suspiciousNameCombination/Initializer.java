public class Initializer {
  public void test() {
    int x = 0;
    int <warning descr="'x' should probably not be assigned to 'y'">y</warning> = x;
  }
}
