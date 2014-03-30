class Test22 {
    static <U> Iterable<U> map(Mapper<String, U> mapper) {
        return null;
    }

    static void test() {
        Iterable<Integer> map = map(Test22 ::length);
    }

    public static <T> int length(String s) {
        return 0;
    }
}
interface Mapper<T, U> {
    U map(T t);
}
