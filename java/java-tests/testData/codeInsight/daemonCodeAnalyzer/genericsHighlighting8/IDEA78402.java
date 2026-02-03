import java.util.Collection;
import java.util.List;

class Reference<T> {}

class Bug {
  private static <T> void foo(List<T> x, Reference<String> y) {
    System.out.println(x);
  }

  private static <T> void foo(Collection<T> x, Reference<T> y) {
    System.out.println(x);
  }

  public static void bazz(List<String> bar) {
    foo<error descr="Ambiguous method call: both 'Bug.foo(List<String>, Reference<String>)' and 'Bug.foo(Collection<String>, Reference<String>)' match">(bar, null)</error>;
  }
}