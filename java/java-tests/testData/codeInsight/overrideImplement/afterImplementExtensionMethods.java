interface A<T> {
    default void m(T t) { }
}

class MyClass<T> implements A<T> {
    @Override
    public void m(T t) {
        <selection>//To change body of implemented methods use File | Settings | File Templates.</selection>
    }
}
