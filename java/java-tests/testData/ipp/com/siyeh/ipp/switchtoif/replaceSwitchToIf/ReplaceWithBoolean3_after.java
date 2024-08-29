import org.jetbrains.annotations.Nullable;

class Test {

  int booleanSwitch3(@Nullable Boolean b) {
      if (<caret>b == Boolean.TRUE) {
          return 1;
      }
      return 2;
  }
}
