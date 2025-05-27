// "Convert to record class" "false"
import org.jetbrains.annotations.NotNull;

class SomeClass {
  static class Prob<caret>lem {
    private final int myLine;
    private final @NotNull String code;
    private final int myColumn;

    // Notice 1: order of parameters differs from order of instance fields
    // Notice 2: second parameter 'line' has incompatible type
    Problem(int column, Object line, @NotNull String code) {
      myLine = line;
      this.code = code;
      myColumn = column;
    }

    static Problem make() {
      int columnArg = 42;
      int lineArg = 0;
      return new Problem(columnArg, lineArg, "");
    }
  }
}
