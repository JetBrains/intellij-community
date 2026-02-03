// "Replace with <>" "false"
public class Test {
  void bar() {
    foo(new FF<Str<caret>ing>());
  }

  void foo(F<F<String>> p) {}
}

class FF<X> extends F<X>{}
class F<T> {}