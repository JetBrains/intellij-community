// "Create field for parameter 'name'" "true"

import org.jetbrains.annotations.NotNull;

class Test {
    void f(@NotNull String <caret>name) {
    }
}