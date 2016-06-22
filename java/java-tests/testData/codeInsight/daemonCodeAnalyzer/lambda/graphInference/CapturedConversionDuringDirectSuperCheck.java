import java.util.List;

class Test {
  void f(List<? extends I<?>> list) {
    foo(list.get(0));
  }

  private <T> T foo(I<T> id) {
    return null;
  }

  interface I<Z> {
  }
}