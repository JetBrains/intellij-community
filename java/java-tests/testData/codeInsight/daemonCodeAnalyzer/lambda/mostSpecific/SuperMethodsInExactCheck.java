import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.Callable;

class Test {
  private static void <warning descr="Private method 'foo(java.lang.Runnable)' is never used">foo</warning>(Runnable runnable) {
    System.out.println(runnable);
  }

  private static void foo(Callable<Iterator<?>> callable) {
    System.out.println(callable);
  }

  public static void main(String[] args) {
    final Collection<Void> collection = new ArrayList<>();
    final Iterable<Void> iterable = collection;
    foo(iterable::iterator);
    foo(collection::iterator);
  }
}
