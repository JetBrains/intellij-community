// "Replace with old style 'switch' statement" "true"
import java.util.*;

class SwitchExpressionMigration {
  int foo(int n) {
      switch (n) {
          case :
              return 12;
          default:
              return 0;
      }
  }
}