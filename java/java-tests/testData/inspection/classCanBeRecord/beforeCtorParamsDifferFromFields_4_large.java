// "Convert to record class" "true-preview"
import org.jetbrains.annotations.NotNull;

class SomeClass {
  public static class Prob<caret>lem {
    private final int myLine;
    private final int myColumn;
    private final @NotNull String code;

    public Problem(int line, int column, @NotNull String code) {
      myLine = line;
      myColumn = column;
      this.code = code;
    }
  }
}
