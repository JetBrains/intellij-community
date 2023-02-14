public class Test {
  public static void main(String[] args, int i) {
    if (args[i] != null) {
      <selection>System.out.println(args[i]);
      System.out.println();</selection>
    }
  }

  void test(String[] args, int i){
    if (args[i] != null) {
      System.out.println(args[i]);
      System.out.println();
    }
  }
}