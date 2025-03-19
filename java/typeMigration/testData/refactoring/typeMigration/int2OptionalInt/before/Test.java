public class Test {
  public static void main(String[] args) {
    test(124);
  }

  static void test(int x) {
    x = 10;
    x++;
    x += 2;
    System.out.println(x);
    System.out.println(x + 1);
  }
}