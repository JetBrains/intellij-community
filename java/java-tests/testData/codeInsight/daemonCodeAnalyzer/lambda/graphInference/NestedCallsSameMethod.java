class Main {
    static <T> T foo(T t) { return null; }

    static {
        long l1 = foo(foo(1));
        Integer i = 1;
        long l2 = foo(foo(i));
    }
}