public class Test {
  A<String> b;

  class Base {

  }

  class A<T> extends Base {
    T value;
  }

  class B<T> extends A<T> {}

  void m() {
    String val = b.value;
  }
}