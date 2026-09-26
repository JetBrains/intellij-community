interface A<T> {
  default void m1(T t) { }
}

interface B<T> extends A<T> {
    
}

class MyClass<T> implements B<T> {
    @Override
    public void m1(T t) {
        <selection><caret>B.super.m1(t);</selection>
    }
}
