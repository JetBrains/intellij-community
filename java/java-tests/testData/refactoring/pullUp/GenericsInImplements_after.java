public class Parent<S> implements I<S> {}

interface I<IT> {
  void method(IT t);
}

class Child<T> extends Parent<T> {
  
  public void method(T t){}
}
