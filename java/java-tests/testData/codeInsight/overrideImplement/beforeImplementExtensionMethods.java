interface A<T> {
    void m(T t) default { }
}

class MyClass<T> implements A<T> {
    <caret>
}
