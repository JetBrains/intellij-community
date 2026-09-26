import org.jetbrains.annotations.Nullable;

class Test {

  int booleanSwitch3(@Nullable Boolean b) {
    switch<caret> (b) {
      case true:
        return 1;
      case null, default:
        return 2;
    }
  }
}
