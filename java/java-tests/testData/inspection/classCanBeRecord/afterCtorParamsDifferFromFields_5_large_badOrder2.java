// "Convert to record class" "true-preview"

import org.jetbrains.annotations.NotNull;

record Problem(int myLine, @NotNull String code, int myColumn) {
    // Notice 1: the names of parameters differs from declaration order of instance fields
    // Notice 2: the order of parameters differs from declaration order of instance fields
    // Notice 3: the order of assigning instance fields differs from their declaration order
    Problem(int line, int column, @NotNull String code) {
        this(line, code, column);
    }

    static Problem make() {
        int lineArg = 0;
        int columnArg = 42;
        return new Problem(lineArg, columnArg, "");
    }
}
