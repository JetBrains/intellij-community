import java.util.Collection;
import java.util.List;

class Main1 {
  static <T> Collection<Collection<T>> foo(Collection<Collection<T>> x) { return x; }

  public static void main(String[] args) {
    List x = null;
    foo(x).iterator().next().<error descr="Cannot resolve method 'iterator()'">iterator</error>();
  }
}

class Main {
  static <T> List<String> foo(Collection<String> x) { return null; }

  public static void main(String[] args) {
    List x = null;
    <error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.lang.String'">String s = foo(x).get(0);</error>
    foo(x).iterator().next().<error descr="Cannot resolve method 'toLowerCase()'">toLowerCase</error>();
  }
}

