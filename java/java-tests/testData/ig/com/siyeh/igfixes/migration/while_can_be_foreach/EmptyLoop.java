import java.util.Iterator;

class MyTest {
  public static void foo(Iterable<?> bar) {
    Iterator<?> it = bar.iterator();
    wh<caret>ile (it.hasNext()) it.next();
  }
}
