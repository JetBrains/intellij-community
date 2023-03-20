// "Create field for parameter 'name'" "true-preview"

import org.jetbrains.annotations.NotNull;

public class TestBefore {

    @NotNull
    private final String myName;

    public TestBefore(@NotNull String name) {
        super();
        myName = name;
    }
}
