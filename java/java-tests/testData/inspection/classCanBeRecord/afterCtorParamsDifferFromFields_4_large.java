// "Convert to record class" "true-preview"

import org.jetbrains.annotations.NotNull;

record Problem(int myLine, int myColumn, @NotNull String code) {
}
