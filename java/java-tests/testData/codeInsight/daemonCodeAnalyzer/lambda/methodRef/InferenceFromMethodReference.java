class Test {
    static <U> Iterable<U> map(Mapper<? super String, ? extends U> mapper) {
        return null;
    }

    static void test() {
        Integer next = map(String::length).iterator().next();
        Integer next1 = map(Test::length).iterator().next();//error with int!!!
    }

    public static <T> T length(T s) {
      return null;
    }

    public static <T> /*int*/Integer length(String s) {
      return 0;
    }
}
interface Mapper<T, U> {
    U map(T t);
}