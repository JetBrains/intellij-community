// "Convert to record class" "true-preview"

import org.jetbrains.annotations.NotNull;

record Problem(int myLine, @NotNull String code, int myColumn) {
    // Notice 1: the names of parameters differs from declaration order of instance fields
    // Notice 2: the order of parameters differs from declaration order of instance fields
    // Notice 3: the order of assigning instance fields differs from their declaration order
    // Notice 4: the types of corresponding parameters<->instance fields match, but have the same type. Easy to overlook!
    Problem(int column, int line, @NotNull String code) {
        this(line, code, column);
    }

    static Problem make() {
        int columnArg = 42;
        int lineArg = 0;
        // Beware here: when converting, we need to make sure arguments are passed the same way as before!
        // This means that we must preserve semantics of the constructor that is called here.
        return new Problem(columnArg, lineArg, "");
    }
}
