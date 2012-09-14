interface A<T> {
    void m1(T t);
    void m2();
}

interface B<T> extends A<T> {
    void m1(T t) default { }
}

class MyClass<T> implements B<T> {
    <caret>
}
