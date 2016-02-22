import java.util.concurrent.Callable;
class Test {

  public void test() {
    Foo<String> f = new Foo<>(() -> "this doesn't compile");
  }

  public class Foo<T> {
    public Foo(Callable<T> supplier) {
    }

    public Foo(T value) {
    }
  }
}