public class ComplexTernaryInSwitch {
  public void test(int x, int y, int z) {
    int a;
    if(switch (x) {
      case 0 -> y < 0 && (a = 5) < z;
      case 1 -> (y > 0 ? (a = 17) : (a = -1)) < z;
      case 2 -> y > 0 ? ((a = 17) < z || 7 > z) : (3 < z || (a = -1) == a);
      default -> (a = y) == 0;
    }) {
      System.out.println(<error descr="Variable 'a' might not have been initialized">a</error>);
    }
  }
}