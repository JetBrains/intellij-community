import org.jetbrains.annotations.Nullable;

class Test {

  int booleanSwitch2(boolean b) {
    <caret>switch (b) {
      case true:
        return 1;
      case false:
        return 2;
    }
  }
}
