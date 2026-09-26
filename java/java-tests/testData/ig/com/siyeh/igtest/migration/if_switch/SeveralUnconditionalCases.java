import org.jetbrains.annotations.NotNull;

class Test {
  private static void test(@NotNull Object l) {
    <info descr="'if' statement can be replaced with 'switch' statement">if</info> (l instanceof int i) {
      System.out.println(i);
    } else if (l instanceof Object i) {
      System.out.println(i);
    } else {
      System.out.println("1");
    }
  }
}