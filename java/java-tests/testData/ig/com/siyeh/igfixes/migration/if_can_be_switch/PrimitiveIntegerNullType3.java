import org.jetbrains.annotations.Nullable;

class Test {
    void test(@Nullable Integer l) {
      i<caret>f (l == 1) {
        System.out.println("3");
      } else if (l == 0) {
        System.out.println("1");
      } else {
        System.out.println("2");
      }
    }
}