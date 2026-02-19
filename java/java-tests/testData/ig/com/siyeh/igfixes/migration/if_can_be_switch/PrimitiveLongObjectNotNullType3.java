import org.jetbrains.annotations.NotNull;

class Test {
    void test(@NotNull Long l) {
      i<caret>f (l == 1L) {
        System.out.println("3");
      } else if (l == 0L) {
        System.out.println("1");
      } else {
        System.out.println("2");
      }
    }
}