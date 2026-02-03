import org.jetbrains.annotations.Nullable;

class Test {
    void test(long l) {
      i<caret>f (l == 1L) {
        System.out.println("3");
      } else if (l == 0L) {
        System.out.println("1");
      } else if (l instanceof long) {
        System.out.println("2");
      }
    }
}