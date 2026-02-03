abstract class A1<X>{
    abstract <T> void foo(T t, X x);
}

class B1<T> extends A1<T>{
  <caret>
}
