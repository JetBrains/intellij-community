import java.util.List;

public class Test {
    private static final List<Class<?>> PRIMITIVE_ARRAY_TYPES = asList(byte[].class, int[].class);
    private static final List<?> PRIMITIVE_ARRAY_TYPES1 = asList(byte[].class, int[].class);

    public static <T> List<T> asList(T... a) {
        return null;
    }
}
