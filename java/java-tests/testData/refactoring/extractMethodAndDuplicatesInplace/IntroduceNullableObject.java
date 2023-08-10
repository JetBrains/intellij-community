public class Test {
  public static int main(boolean param) {
    <selection>int x = 0;
    int y = 0;
    if (param) return -1;
    if (Math.random() > 0.5) return -1;
    System.out.println();</selection>

    System.out.println("Point(" + x + ", " + y+ ")");
    return 0;
  }
}