// "Bind constructor parameters to fields" "true-preview"

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestBefore {

    public TestBefore(@NotNull String name<caret>, @NotNull String name2) {
        super();
    }
}
