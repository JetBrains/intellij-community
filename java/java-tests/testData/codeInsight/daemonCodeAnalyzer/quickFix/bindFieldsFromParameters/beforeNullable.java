// "Bind constructor parameters to fields" "true"

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestBefore {

    public TestBefore(@Nullable String name<caret>, @Nullable String name2) {
        super();
    }
}
