// "Replace method call on lambda with lambda body" "true-preview"
import java.util.function.Supplier;

class Test {
  public static int test() {
    for (int i = 0; i < 10; i++) {
      final int finalI = i;
        System.out.println("hello");
        try {
          if (finalI > 3) {
            return 123;
          }
          System.out.println(finalI);
        } catch (Exception ignored) {
          return 456;
        }
        return 456;
    }
    System.out.println("Hello");
    return 3;
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}