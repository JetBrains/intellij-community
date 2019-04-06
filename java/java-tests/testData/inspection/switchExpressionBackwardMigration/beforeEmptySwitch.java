// "Replace with old style 'switch' statement" "false"
import java.util.*;

class SwitchExpressionMigration {
  void foo(int n) {
    <caret>switch (n) {
    }
  }
}