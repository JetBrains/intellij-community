import org.jetbrains.annotations.NotNull;

class Test {
    void test(@NotNull Integer l) {
      i<caret>f (l == 1) {
        System.out.println("3");
      } else if (l == 0) {
        System.out.println("1");
      } else {
        System.out.println("2");
      }
    }
}