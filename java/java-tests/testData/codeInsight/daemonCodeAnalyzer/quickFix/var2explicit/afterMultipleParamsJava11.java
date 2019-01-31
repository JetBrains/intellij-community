// "Replace 'var' with explicit type" "true"
import java.util.function.BiFunction;

final class Example {
    void m() {
        BiFunction<Integer, ? extends String, Integer> graph = (Integer x1, String x) -> x1*2;
    }
}