import java.util.ArrayList;
import java.util.List;

class Test {
  List<String> queue = new ArrayList<>();
}

class DD {
    P1<P<String>> l = new L<String>() {
        @Override
        void f() {
        }
    };

    P1<P<String>> l1 = new L<>();

    P1<P<String>> foo() {
        return new L<>();
    }

    String s = "";
}

class L<K> extends P1<P<K>> {
    void f() {
    }
}

class P1<P1T> extends P<P1T> {
}

class P<PT> {
}


class Test1 {
  void bar() {
    foo(new FF<>());
  }

  void foo(F<F<String>> p) {}
}

class FF<X> extends F<X>{}
class F<T> {}