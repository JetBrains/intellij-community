// "Convert to record class" "true-preview"

import org.jetbrains.annotations.NotNull;

class Problem<caret> {
  private final int myLine;
  private final int myColumn;
  private final @NotNull String code;

  Problem(int line, int column, @NotNull String code) {
    myLine = line;
    myColumn = column;
    this.code = code;
  }
}
