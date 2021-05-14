interface A<T> {
  default void m1(T t) { }
}

interface B<T> extends A<T> {
    
}

class MyClass<T> implements B<T> {
    <caret>
}
