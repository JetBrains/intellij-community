package p;

import java.util.function.BiFunction;
import java.util.function.Predicate;

class MyTest {
    void m(Predicate<String> p) {}
    void m(BiFunction<String, Integer, String> f) {}

    {
        m((s, i) -> {//m should be resolved here
            if (s.equals("a")) {

            }
        <error descr="Missing return statement">}</error>);
    }
}
