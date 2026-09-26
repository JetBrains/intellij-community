
import java.io.InputStream;
import java.util.function.Function;
import java.util.function.Supplier;

class MyTest {
    
    public static <W> void m(Class<W> targetType, Supplier<InputStream> upstream) {
        Supplier<Supplier<W>> downstream = mapping(
                (InputStream is) -> () -> readValue(is, targetType));
    }

    public static <T,U> Supplier<U> mapping(Function<? super T, ? extends U> mapper) {
        return null;
    }

    public static <W> W readValue(InputStream stream, Class<W> targetType) {
        return null;
    }
    
}
