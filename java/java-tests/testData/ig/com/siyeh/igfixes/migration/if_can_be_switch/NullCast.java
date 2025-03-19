import org.jetbrains.annotations.Nullable;

class Test {
  int foo(@Nullable Integer x) {
    i<caret>f (x == null) {
      return 123;
    }
    throw new IllegalStateException("Unexpected value: " + x);
  }
}