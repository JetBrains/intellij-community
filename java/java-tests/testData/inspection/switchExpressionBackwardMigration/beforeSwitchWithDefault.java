// "Fix all 'Enhanced 'switch'' problems in file" "true"
import java.util.*;

class SwitchWithDefault {
  int booleanSwitch(boolean b) {
    return swit<caret>ch (b) {
      case true -> 1;
      case false -> 2;
    };
  }

  int booleanSwitch2(Boolean b) {
    return switch (b) {
      case true -> 1;
      case false -> 2;
    };
  }

  int booleanSwitch3(Boolean b) {
    int i = switch (b) {
      case true -> 1;
      case false -> 2;
    };
  }
}