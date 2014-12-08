// "Bind constructor parameters to fields" "true"

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestBefore {

    @Nullable
    private final String myName;
    @Nullable
    private final String myName2;

    public TestBefore(@Nullable String name, @Nullable String name2) {
        super();
        myName = name;
        myName2 = name2;
    }
}
