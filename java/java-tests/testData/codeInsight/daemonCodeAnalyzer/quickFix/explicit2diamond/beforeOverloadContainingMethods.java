// "Replace with <>" "false"
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntToLongFunction;

abstract class SimplePlanet {
    void a(IntToLongFunction r, List<String> l) {}
    void a(Function<Integer, Integer> f, List<Number> l) {}

    {
        a(a -> a, new ArrayList<Str<caret>ing>());
    }
}