import org.jetbrains.annotations.Nullable;

class Test {

  int booleanSwitch1(@Nullable Boolean b) {
      i<caret>f (b) {
          return 1;
      } else {
          return 2;
      }
  }
}
