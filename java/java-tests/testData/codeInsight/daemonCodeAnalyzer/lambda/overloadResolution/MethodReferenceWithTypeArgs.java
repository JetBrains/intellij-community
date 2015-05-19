
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Stream;

class Test {

  void foo() {
    log(get(TreeSet<String>::new));
  }

  private void <warning descr="Private method 'log(java.lang.String[])' is never used">log</warning>(String params[]) {
    System.out.println(params);
  }
  private void log(Object params) {
    System.out.println(params);
  }

  <C> C get(Supplier<C> s) {
    return s.get();
  }
}