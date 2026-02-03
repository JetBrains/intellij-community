public abstract class Parent<S> {
    abstract void method(S t);
}

class Child<T> extends Parent<T> {
   @Override
   void method(T t){}
}
