interface A<T> {
    default void m(T t) { }
}

class MyClass<T> implements A<T> {
    <caret>
}
