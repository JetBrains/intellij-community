// "Convert to record class" "true-preview"

import org.jetbrains.annotations.NotNull;

record Problem(int myLine, int myColumn, @NotNull String code) {
    // Notice: the names of parameters differs from declaration order of instance fields
}
