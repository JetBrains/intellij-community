
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

class MyTest {

    {
        foo(() -> build(id(x -> x)));
    }

    static <I> I id(I i) {
      return i;
    }
    static <K> void foo(Runnable e) {}
    
    static <TT> List<TT> build(Function<String, TT> transformers) {
        return null;
    }
}
