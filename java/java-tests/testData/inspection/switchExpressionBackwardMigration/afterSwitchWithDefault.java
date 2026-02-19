// "Fix all 'Enhanced 'switch'' problems in file" "true"
import java.util.*;

class SwitchWithDefault {
  int booleanSwitch(boolean b) {
      switch (b) {
          case true:
              return 1;
          case false:
              return 2;
      }
  }

  int booleanSwitch2(Boolean b) {
      switch (b) {
          case true:
              return 1;
          case false:
              return 2;
      }
  }

  int booleanSwitch3(Boolean b) {
      int i;
      switch (b) {
          case true:
              i = 1;
              break;
          case false:
              i = 2;
              break;
      }
  }
}