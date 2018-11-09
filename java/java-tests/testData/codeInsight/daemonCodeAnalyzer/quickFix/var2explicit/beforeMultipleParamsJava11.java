// "Replace 'var' with explicit type" "true"
import java.util.function.BiFunction;

final class Example {
    void m() {
        BiFunction<Integer, ? extends String, Integer> graph = (var x1, <caret>var x) -> x1*2;
    }
}