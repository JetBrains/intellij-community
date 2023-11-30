import org.jetbrains.annotations.Nullable;

class Test {
  int foo(@Nullable Integer x) {
      <caret>return switch (x) {
          case null -> 123;
          default -> throw new IllegalStateException("Unexpected value: " + x);
      };
  }
}