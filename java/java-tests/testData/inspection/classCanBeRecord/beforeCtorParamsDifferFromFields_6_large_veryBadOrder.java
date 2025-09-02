// "Convert to record class" "true-preview"
import org.jetbrains.annotations.NotNull;

class SomeClass {
  static class Prob<caret>lem {
    private final int myLine;
    private final @NotNull String code;
    private final int myColumn;

    // Notice: order of parameters differs from order of instance fields 
    Problem(int column, int line, @NotNull String code) {
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
