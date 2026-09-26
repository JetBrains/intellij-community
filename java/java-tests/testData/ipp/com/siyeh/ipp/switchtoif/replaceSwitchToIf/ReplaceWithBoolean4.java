import org.jetbrains.annotations.Nullable;

class Test {

  int booleanSwitch4(@Nullable Boolean b) {
    switch<caret> (b) {
      case true:
        return 1;
      case null:
        return 0;
      case false:
        return 2;
    }
  }
}
