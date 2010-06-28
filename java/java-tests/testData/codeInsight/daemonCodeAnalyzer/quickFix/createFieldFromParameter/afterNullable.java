// "Create Field For Parameter 'name'" "true"

package codeInsight.createFieldFromParameterAction.test1;

import java.util.HashMap;
import org.jetbrains.annotations.Nullable;

public class TestBefore {

    @Nullable
    private final String myName;

    public TestBefore(@Nullable String name) {
        super();
        myName = name;
    }
}
