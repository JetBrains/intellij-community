// "Replace method reference with lambda" "true-preview"
public class Foo {
  public static class Bar {}
}

class Goo {
  void test(){
    Supplier<Foo.Bar> s = () -> new Foo.Bar();
  }
}

interface Supplier<T> {
  T get();
}