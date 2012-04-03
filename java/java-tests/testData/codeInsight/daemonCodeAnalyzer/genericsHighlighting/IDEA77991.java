class Test {
    static {
        Class<Test> testClass = get(Test.class);
        foo(testClass);
    }

    static <E> Class<E> get(Class<? super E> value) {
        return null;
    }

    static <E> E foo(Class<? super E> value) {
        return null;
    }
}