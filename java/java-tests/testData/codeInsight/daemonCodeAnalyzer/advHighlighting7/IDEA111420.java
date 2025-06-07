import java.util.List;

public class Test {
    private static final List<Class<?>> PRIMITIVE_ARRAY_TYPES = <error descr="Incompatible types. Found: 'java.util.List<java.lang.Class<? extends java.lang.Cloneable & java.io.Serializable>>', required: 'java.util.List<java.lang.Class<?>>'">asList</error>(byte[].class, int[].class);
    private static final List<?> PRIMITIVE_ARRAY_TYPES1 = asList(byte[].class, int[].class);

    public static <T> List<T> asList(T... a) {
        return null;
    }
}
