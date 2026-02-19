
import java.util.function.BiFunction;
import java.util.function.Function;

public class Clazz {
    
    <T> T foo(BiFunction<T, T, T> d) { return null;}
    <T> T foo(Function<T, String> d) { return null;}

    {
        fooBar(foo(this::<caret>bar));
    }
    
    <K> void fooBar(K k) {}
}


