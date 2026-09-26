import org.jetbrains.annotations.Nullable;

class Test {

  int booleanSwitch1(@Nullable Boolean b) {
    swi<caret>tch (b) {
      case true:
        return 1;
      case false:
        return 2;
    }
  }
}
