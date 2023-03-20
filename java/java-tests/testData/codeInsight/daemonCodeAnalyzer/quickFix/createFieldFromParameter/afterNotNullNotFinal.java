// "Create field for parameter 'name'" "true-preview"

import org.jetbrains.annotations.NotNull;

class Test {
    private String myName;

    void f(@NotNull String name) {
        myName = name;
    }
}