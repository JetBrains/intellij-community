interface Runnable{}
interface Class<T>{}

public class Foo implements Runnable{
  static <T> void foo(Class<T> aClass, T t) { }

  Class<Foo> getC(){ return new Class<Foo>(){};}
  public void run() {
  }

  {
    <caret>foo(new Class<Runnable>(){}, this);
  }
}