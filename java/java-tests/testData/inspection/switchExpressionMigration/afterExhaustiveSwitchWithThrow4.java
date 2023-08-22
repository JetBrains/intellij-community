// "Replace with 'switch' expression" "true-preview"
import org.jetbrains.annotations.Nullable;

class X {

  int test4(int x) {
      return switch (x) {
          case 1 -> 2;
          case 2 -> 4;
          default -> throw new IllegalArgumentException();
      };
  }
}