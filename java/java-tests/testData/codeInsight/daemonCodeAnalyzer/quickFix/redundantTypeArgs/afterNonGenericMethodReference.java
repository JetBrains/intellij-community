// "Remove type arguments" "true-preview"

import java.util.function.Function;
class MyTest {

    {
        Function<String, Integer> r = Integer::getInteger;
    }
}

