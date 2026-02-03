public abstract class Parent<S> {}

class Child<T> extends Parent<T> {
   void <caret>method(T t){}
}
