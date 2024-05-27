import org.jetbrains.annotations.Nullable;

class Test {
    void test(int l) {
      i<caret>f (1 == l) {
        System.out.println("3");
      } else if (0 == l) {
        System.out.println("1");
      } else if (l instanceof Integer) {
        System.out.println("2");
      }
    }
}