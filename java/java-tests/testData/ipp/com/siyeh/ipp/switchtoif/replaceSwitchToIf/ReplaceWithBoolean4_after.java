import org.jetbrains.annotations.Nullable;

class Test {

  int booleanSwitch4(@Nullable Boolean b) {
      if (<caret>b == Boolean.TRUE) {
          return 1;
      } else if (b == null) {
          return 0;
      } else {
          return 2;
      }
  }
}
