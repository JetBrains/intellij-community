package x;

public class Test<T> {
  public void foo(T t) {
      //Tracked f;
  }
}

class Goo<T> extends Test<T> {
    public void foo(T t) {}
}
class Goo  {

}

class Zoo extends Goo {
    public void foo(Object t) {}
}
