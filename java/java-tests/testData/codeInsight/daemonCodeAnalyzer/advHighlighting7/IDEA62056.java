class Test {
    public static void rotate(S<?> s) {
        Object[] o = new Object[0];
        s.call(o);
    }
}

interface S<T>{
    void call(T... elements);
}