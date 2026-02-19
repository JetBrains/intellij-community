import java.util.Iterator;

class MyTest {
  public static void foo(Iterable<?> bar) {
      for (Object o : bar) ;
  }
}
