
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Stream;

class Test {

  void foo() {
    log(<error descr="Incompatible types. Found: 'java.util.TreeSet<java.lang.String>', required: 'java.lang.String[]'">get</error>(TreeSet<String>::new));
  }

  private void log(String params[]) {
    System.out.println(params);
  }
  private void <warning descr="Private method 'log(java.lang.Object)' is never used">log</warning>(Object params) {
    System.out.println(params);
  }

  <C> C get(Supplier<C> s) {
    return s.get();
  }
}