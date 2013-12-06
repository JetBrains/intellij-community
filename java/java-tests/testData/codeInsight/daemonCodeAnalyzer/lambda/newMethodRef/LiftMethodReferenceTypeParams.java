import java.util.List;

class Test {

    interface Function<K, V> {
        V _(K k);
    }

    static {
        foo(Test::asList);
    }

    public static <T> List<T> asList(T... a) {
        return null;
    }

    public static <C> void foo(Function<String, C> fn) {  }
}