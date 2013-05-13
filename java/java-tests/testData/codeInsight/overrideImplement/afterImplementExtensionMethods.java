interface A<T> {
    default void m(T t) { }
}

class MyClass<T> implements A<T> {
    @Override
    public void m(T t) {
        <caret>
    }
}
