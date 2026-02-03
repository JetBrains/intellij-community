
import java.util.function.Function;

class MyTest {

    static <T, R, F extends Function<T, R>> F from(F fun1) {
        return fun1;
    }

    void test2() {
        Function<Object, Integer> ext = from(x -> 1);
    }
}