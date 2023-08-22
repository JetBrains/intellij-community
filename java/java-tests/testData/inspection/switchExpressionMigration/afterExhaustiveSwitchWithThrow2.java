// "Replace with 'switch' expression" "true-preview"
import org.jetbrains.annotations.Nullable;

class X {
  int test2(int x) {
      return switch (x) {
          case 1 -> 2;
          case 2 -> 4;
          case 3 -> 6;
          default -> throw new IllegalArgumentException();
      };
  }
}