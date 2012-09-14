interface A<T> {
    void m1(T t);
    void m2();
}

interface B<T> extends A<T> {
    void m1(T t) default { }
}

class MyClass<T> implements B<T> {
    @Override
    public void m2() {
        <selection>//To change body of implemented methods use File | Settings | File Templates.</selection>
    }
}
