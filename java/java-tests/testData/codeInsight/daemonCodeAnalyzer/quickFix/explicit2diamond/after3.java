// "Replace with <>" "true"
public class Test {
  void bar() {
    foo(new FF<>());
  }

  void foo(F<F<String>> p) {}
}

class FF<X> extends F<X>{}
class F<T> {}