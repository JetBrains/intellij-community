class Test {
    static <U> Iterable<U> map(Mapper<? super String, ? extends U> mapper) {
        return null;
    }

    static void test() {
        Integer next = map(String::length).iterator().next();
        <error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.lang.Integer'">Integer next1 = map(Test::length).iterator().next();</error>
    }

    public static <T> T length(T s) {
      return null;
    }

    public static <T> int length(String s) {
      return 0;
    }
}
interface Mapper<T, U> {
    U map(T t);
}