import java.util.List;

public class Test {
    private static final <error descr="Incompatible types. Found: 'java.util.List<java.lang.Class<? extends java.lang.Cloneable & java.io.Serializable>>', required: 'java.util.List<java.lang.Class<?>>'">List<Class<?>> PRIMITIVE_ARRAY_TYPES = asList(byte[].class, int[].class);</error>
    private static final List<?> PRIMITIVE_ARRAY_TYPES1 = asList(byte[].class, int[].class);

    public static <T> List<T> asList(T... a) {
        return null;
    }
}
