import java.util.*;

class T {
  void f(Set<String> t, String[] f, int a, int b) {
      t.addAll(Arrays.asList(f).subList(a, b));
  }
}