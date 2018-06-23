// "Remove type arguments" "true"

import java.util.function.Function;
public class TestClassRenamed {

    {
        Function<String, Integer> r = this::foo;
    }

    private <T> T foo(String s) {
        return null;
    }
}

