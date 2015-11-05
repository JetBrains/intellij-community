import java.util.ArrayList;
import java.util.stream.Stream;

public class Main17 {

  interface A {
    Stream<String> getIterable();
  }

  class B implements A {
    @Override
    public Stream<String> getIterable() {
      return new ArrayList<String>().stream().map(s -> s.intern());
    }
  }

  static void m(A a) {
    int s = (int) a.getIterable().map(s1 -> s1).count();
  }

  static void m2(B b) {
    int s = (int) b.getIterable().count();
  }
}
