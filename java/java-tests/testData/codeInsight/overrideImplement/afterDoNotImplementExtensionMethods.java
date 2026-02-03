interface A<T> {
    void m1(T t);
    void m2();
}

interface B<T> extends A<T> {
    default void m1(T t) { }
}

class MyClass<T> implements B<T> {
    @Override
    public void m2() {
        <caret>
    }
}
