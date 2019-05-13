// "Bind constructor parameters to fields" "true"

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestBefore {

    @NotNull
    private final String myName;
    @NotNull
    private final String myName2;

    public TestBefore(@NotNull String name, @NotNull String name2) {
        super();
        myName = name;
        myName2 = name2;
    }
}
