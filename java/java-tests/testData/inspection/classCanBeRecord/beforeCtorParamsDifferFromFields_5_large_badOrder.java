// "Convert to record class" "true-preview"
import org.jetbrains.annotations.NotNull;

class SomeClass {
  static class Prob<caret>lem {
    private final int myLine;
    private final @NotNull String code;
    private final int myColumn;

    Problem(int line, int column, @NotNull String code) {
      myLine = line;
      this.code = code;
      myColumn = column;
    }

    static Problem make() {
      int lineArg = 0;
      int columnArg = 42;
      return new Problem(lineArg, columnArg, "");
    }
  }
}
