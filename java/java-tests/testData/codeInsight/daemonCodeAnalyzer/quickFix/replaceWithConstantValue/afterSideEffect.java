// "Replace with '0'|->Extract possible side effects" "true-preview"
import java.util.stream.*;

class Test {
  public static void method() {
    int a = 1;
    int b = 0;

      b = a;
      a = 0;
  }
}