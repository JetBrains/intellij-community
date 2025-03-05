import java.util.Collection;
import java.util.List;

class Main1 {
  static <T> Collection<Collection<T>> foo(Collection<Collection<T>> x) { return x; }

  public static void main(String[] args) {
    List x = null;
    foo(x).iterator().next().<error descr="Cannot resolve method 'iterator' in 'Object'">iterator</error>();
  }
}

class Main {
  static <T> List<String> foo(Collection<String> x) { return null; }

  public static void main(String[] args) {
    List x = null;
    String s = foo(x).<error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.lang.String'">get</error>(0);
    foo(x).iterator().next().<error descr="Cannot resolve method 'toLowerCase' in 'Object'">toLowerCase</error>();
  }
}

