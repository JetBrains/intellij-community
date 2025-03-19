import org.jetbrains.annotations.NotNull;

class Test {
  private static void test(int l) {
    <info descr="'if' statement can be replaced with 'switch' statement">if</info> (l == 1) {
      System.out.println("2");
    } else if (l instanceof int i) {
      System.out.println(i);
    } else if (l instanceof long i) {
      System.out.println(i);
    }
  }
}