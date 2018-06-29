// "Remove type arguments" "true"

import java.util.function.Function;
class MyTest {

    {
        Function<String, Integer> r = Integer::getInteger;
    }
}

